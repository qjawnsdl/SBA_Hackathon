import os
from dotenv import load_dotenv
from flask import Flask, request, jsonify

from langchain_google_genai import ChatGoogleGenerativeAI, GoogleGenerativeAIEmbeddings
from langchain_community.vectorstores import FAISS
from langchain_text_splitters import RecursiveCharacterTextSplitter
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.output_parsers import StrOutputParser
from langchain_core.runnables import RunnableParallel, RunnablePassthrough

app = Flask(__name__)
rag_chain = None
vector_store = None


@app.route('/', methods=['GET'])
def index():
    return "RAG 챗봇 서버가 실행 중입니다. POST /chat 경로로 질문을 전송하세요."


#
# --- (수정된 부분 시작) ---
#
def setup_rag_pipeline():
    global rag_chain, vector_store

    load_dotenv("a.env")
    if "GOOGLE_API_KEY" not in os.environ:
        print("GOOGLE_API_KEY 환경변수 없음")
        os._exit(1)

    try:
        llm = ChatGoogleGenerativeAI(model="gemini-1.5-flash")  # gemini-2.5-flash -> gemini-1.5-flash (오타 수정)
        embeddings = GoogleGenerativeAIEmbeddings(model="models/text-embedding-004")  # model -> models/ (경로 수정)
    except Exception as e:
        print(f"Gemini 모델 초기화 실패: {e}")
        print("GOOGLE_API_KEY가 유효한지 또는 a.env 파일이 올바른지 확인하세요.")
        os._exit(1)

    manual_file = "예비군편성.txt"
    try:
        with open(manual_file, "r", encoding="utf-8") as f:
            manual_text = f.read()
    except FileNotFoundError:
        print(f"오류: '{manual_file}'을 찾을 수 없습니다.")
        os._exit(1)

    text_splitter = RecursiveCharacterTextSplitter(chunk_size=500, chunk_overlap=50)

    # 1. 문서를 먼저 청크(조각)로 분할합니다.
    #    (이때 메타데이터를 전달하지 않습니다)
    documents = text_splitter.create_documents([manual_text])

    # 2. 분할된 각 Document 객체에 반복문으로 고유 인덱스를 부여합니다.
    for i, doc in enumerate(documents):
        doc.metadata["index"] = i

    print(f"'{manual_file}' 파일을 총 {len(documents)}개의 청크로 분할하고 인덱스를 부여했습니다.")

    # 3. 이제 고유 인덱스를 가진 documents로 벡터 스토어를 생성합니다.
    vector_store = FAISS.from_documents(documents, embeddings)
    retriever = vector_store.as_retriever(search_kwargs={"k": 3})

    prompt = ChatPromptTemplate.from_template("""
당신은 친절하고 정확한 AI 상담원입니다.
[참고 매뉴얼]: {context}
[질문]: {input}
[답변]:
""")
    rag_chain = (
            RunnableParallel({
                "context": retriever,
                "input": RunnablePassthrough(),
            }) | prompt | llm | StrOutputParser()
    )
    print("RAG 파이프라인 설정 완료.")


#
# --- (수정된 부분 끝) ---
#

def export_manual_index(filename="예비군인덱스.txt"):
    global vector_store
    if vector_store is None:
        raise Exception("벡터스토어가 준비되지 않았습니다.")

    # FAISS.docstore._dict 접근이 안정적이지 않을 수 있으므로,
    # 인덱스 자체(vector_store.index)에서 정보를 가져오도록 시도합니다. (대안)
    # 하지만 FAISS 래퍼가 문서를 docstore에 저장하므로 기존 로직도 작동해야 합니다.
    # docstore가 비어있다면 setup_rag_pipeline에 문제가 있는 것입니다.

    if not vector_store.docstore._dict:
        print("경고: docstore가 비어있습니다. 인덱싱이 잘못되었을 수 있습니다.")
        return

    # docstore의 문서를 인덱스 기준으로 정렬
    sorted_docs = sorted(vector_store.docstore._dict.values(),
                         key=lambda doc: doc.metadata.get("index", 0))

    with open(filename, "w", encoding="utf-8") as f:
        for doc in sorted_docs:
            idx = doc.metadata.get("index", "N/A")  # 이제 고유한 인덱스가 나옵니다.
            f.write(f"--- INDEX: {idx} ---\n")
            f.write(doc.page_content.strip() + "\n\n")
    print(f"{filename}로 저장 완료")


@app.route('/chat', methods=['POST'])
def chat():
    global rag_chain
    if rag_chain is None:
        return jsonify({"error": "서버가 준비되지 않았습니다."}), 503

    data = request.json
    if not data or 'query' not in data:
        return jsonify({"error": "질문(query)이 누락되었습니다."}), 400

    user_query = data['query']
    try:
        answer = rag_chain.invoke(user_query)
        return jsonify({"answer": answer})
    except Exception as e:
        print(f"RAG 체인 실행 오류: {e}")
        return jsonify({"error": "답변 생성 중 오류가 발생했습니다."}), 500


@app.route('/manual_indexes', methods=['GET'])
def manual_indexes():
    global vector_store
    if vector_store is None:
        return jsonify({"error": "벡터스토어가 준비되지 않았습니다."}), 503

    chunks = []

    # docstore의 문서를 인덱스 기준으로 정렬
    sorted_docs = sorted(vector_store.docstore._dict.values(),
                         key=lambda doc: doc.metadata.get("index", 0))

    for doc in sorted_docs:
        chunks.append({
            "index": doc.metadata.get("index", "N/A"),
            "text": doc.page_content
        })
    return jsonify(chunks)


@app.route('/export_manual', methods=['GET'])
def export_manual():
    try:
        export_manual_index("예비군인덱스.txt")
        return jsonify({"message": "예비군인덱스.txt로 저장 완료"}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500


if __name__ == '__main__':
    setup_rag_pipeline()
    export_manual_index("예비군인덱스.txt")  # 서버 시작할 때 자동 생성
    from waitress import serve

    print("Waitress 서버를 0.0.0.0:5000에서 시작합니다.")
    serve(app, host='0.0.0.0', port=5000)
