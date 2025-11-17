import os
from dotenv import load_dotenv
from flask import Flask, request, jsonify
import urllib.parse  # 한글 경로(폴더명) 인코딩용

from langchain_google_genai import ChatGoogleGenerativeAI, GoogleGenerativeAIEmbeddings
from langchain_community.vectorstores import FAISS
from langchain_text_splitters import RecursiveCharacterTextSplitter
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.output_parsers import StrOutputParser
from langchain_core.runnables import RunnableParallel, RunnablePassthrough

# --- (전역 변수 및 상수) ---
app = Flask(__name__)

# 인덱스(index.faiss)를 저장할 영구 폴더 경로
VECTOR_STORE_DIR = r"D:\rag_storage"

llm = None
embeddings = None
global_pipelines = {}  # { "기업명": rag_chain } (RAM)
global_vector_stores = {}  # { "기업명": vector_store } (RAM)
global_general_chain = None


# --- (전역 변수 끝) ---


@app.route('/', methods=['GET'])
def index():
    return "RAG 챗봇 서버(다중 기업 지원/영구 저장)가 실행 중입니다."


# --- (★★ 신규 함수: .txt 파일로 내보내기 ★★) ---
def export_chunks_to_txt(companyName, vector_store):
    """
    벡터 스토어의 내용을 사람이 읽을 수 있는 .txt 파일로 저장합니다.
    (app.py와 같은 폴더에 저장됩니다.)
    """
    filename = f"{companyName}_인덱스.txt"
    print(f"'{filename}' 파일 생성을 시작합니다...")
    try:
        if not hasattr(vector_store, 'docstore') or not vector_store.docstore._dict:
            print(f"경고: '{companyName}' 벡터 스토어에 docstore 내용이 없습니다.")
            return

        sorted_docs = sorted(vector_store.docstore._dict.values(),
                             key=lambda doc: doc.metadata.get("index", 0))

        with open(filename, "w", encoding="utf-8-sig") as f:
            f.write(f"[{companyName} 매뉴얼 인덱스 조각 모음]\n")
            f.write(f"총 {len(sorted_docs)}개의 조각이 있습니다.\n")
            f.write("=" * 30 + "\n\n")

            for doc in sorted_docs:
                idx = doc.metadata.get("index", "N/A")
                f.write(f"--- INDEX: {idx} ---\n")
                f.write(doc.page_content.strip() + "\n\n")

        print(f"'{filename}' 파일이 app.py와 같은 폴더에 성공적으로 저장되었습니다.")

    except Exception as e:
        print(f"'{filename}' 파일 저장 중 오류 발생: {e}")


# --- (신규 함수 끝) ---


# --- (★★ 수정된 함수: k=5) ---
def create_rag_pipeline_for_manual(manual_text, llm_model, embeddings_model):
    """
    주어진 텍스트로 RAG 파이프라인과 벡터 스토어를 생성합니다.
    """
    text_splitter = RecursiveCharacterTextSplitter(chunk_size=500, chunk_overlap=50)
    documents = text_splitter.create_documents([manual_text])
    for i, doc in enumerate(documents):
        doc.metadata["index"] = i
    print(f"총 {len(documents)}개의 청크로 분할하고 인덱스를 부여했습니다.")

    vector_store = FAISS.from_documents(documents, embeddings_model)

    # ★ k=3 에서 k=5 로 수정 (더 많은 조각 참고)
    retriever = vector_store.as_retriever(search_kwargs={"k": 10})

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
            }) | prompt | llm_model | StrOutputParser()
    )
    print("RAG 파이프라인 생성 완료 (k=5).")
    return rag_chain, vector_store


# --- (수정된 함수 끝) ---


# --- (★★ 수정된 함수: k=5) ---
def load_rag_pipeline_from_disk(companyName, llm_model, embeddings_model):
    """
    디스크(D:\rag_storage)에서 FAISS 인덱스를 로드하여 RAG 파이프라인을 복원합니다.
    """
    safe_folder_name = urllib.parse.quote_plus(companyName)
    index_path = os.path.join(VECTOR_STORE_DIR, f"{safe_folder_name}_index")
    if not os.path.exists(index_path):
        return None, None
    try:
        vector_store = FAISS.load_local(
            index_path, embeddings_model, allow_dangerous_deserialization=True
        )

        # ★ k=3 에서 k=5 로 수정 (더 많은 조각 참고)
        retriever = vector_store.as_retriever(search_kwargs={"k": 5})

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
                }) | prompt | llm_model | StrOutputParser()
        )
        print(f"'{companyName}' 인덱스를 디스크에서 RAM으로 로드 완료 (k=5).")
        return rag_chain, vector_store
    except Exception as e:
        print(f"'{companyName}' 인덱스 로드 중 오류 발생: {e}")
        return None, None


# --- (수정된 함수 끝) ---


@app.route('/upload', methods=['POST'])
def upload_manual():
    """
    기업용 파일 업로드 API (RAM 저장 + Disk 저장 + .txt 파일 생성)
    (이 함수는 k=5를 적용하기 위해 create_rag_pipeline_for_manual을 호출)
    """
    global llm, embeddings, global_pipelines, global_vector_stores

    if 'file' not in request.files:
        return jsonify({"error": "파일(file)이 없습니다."}), 400
    if 'companyName' not in request.form:
        return jsonify({"error": "기업명(companyName)이 없습니다."}), 400

    file = request.files['file']
    companyName = request.form['companyName']

    if file.filename == '':
        return jsonify({"error": "파일이 선택되지 않았습니다."}), 400
    if companyName == "병무청":
        return jsonify({"error: '병무청'은 기본값이므로 업로드할 수 없습니다."}), 400

    try:
        manual_text = file.read().decode('utf-8')
        # k=5가 적용된 생성 함수 호출
        (chain, store) = create_rag_pipeline_for_manual(manual_text, llm, embeddings)

        # 1. 디스크에 index.faiss 저장
        safe_folder_name = urllib.parse.quote_plus(companyName)
        os.makedirs(VECTOR_STORE_DIR, exist_ok=True)
        index_path = os.path.join(VECTOR_STORE_DIR, f"{safe_folder_name}_index")
        store.save_local(index_path)

        # 2. RAM에 저장
        global_pipelines[companyName] = chain
        global_vector_stores[companyName] = store

        # 3. .txt 파일로 내보내기
        export_chunks_to_txt(companyName, store)

        print(f"'{companyName}' 파이프라인 생성 및 [디스크]({index_path})에 저장 완료.")
        return jsonify({"message": f"{companyName} 매뉴얼 업로드 성공", "companyName": companyName}), 200

    except Exception as e:
        print(f"'/upload' 처리 오류: {e}")
        return jsonify({"error": "파일 처리 중 오류 발생"}), 500


@app.route('/chat', methods=['POST'])
def chat():
    """ 사용자용 채팅 API (수정 없음) """
    global global_pipelines, global_general_chain
    data = request.json
    if not data or 'query' not in data:
        return jsonify({"error": "질문(query)이 누락되었습니다."}), 400
    user_query = data['query']
    companyName = data.get('companyName')
    try:
        answer = ""
        if companyName:
            rag_chain = global_pipelines.get(companyName)
            if rag_chain is None:
                return jsonify({"error": f"'{companyName}'에 해당하는 상담원이 없습니다."}), 404
            answer = rag_chain.invoke(user_query)
        else:
            if global_general_chain is None:
                return jsonify({"error": "일반 상담 기능이 준비되지 않았습니다."}), 500
            answer = global_general_chain.invoke(user_query)
        return jsonify({"answer": answer})
    except Exception as e:
        print(f"'/chat' RAG 체인 실행 오류: {e}")
        return jsonify({"error": "답변 생성 중 오류가 발생했습니다."}), 500


@app.route('/manual_indexes', methods=['GET'])
def get_manual_indexes():
    """ 매뉴얼 조각(인덱스) 조회 API (수정 없음) """
    global global_vector_stores
    companyName = request.args.get('company')
    if not companyName:
        return jsonify({"error": "기업명(company) 쿼리 파라미터가 필요합니다."}), 400
    if companyName not in global_vector_stores:
        return jsonify({"error": f"'{companyName}'에 해당하는 매뉴얼 인덱스가 없습니다."}), 404
    vector_store = global_vector_stores[companyName]
    if not hasattr(vector_store, 'docstore') or not vector_store.docstore._dict:
        return jsonify({"error": "인덱싱된 데이터가 없습니다."}), 500
    chunks = []
    try:
        sorted_docs = sorted(vector_store.docstore._dict.values(),
                             key=lambda doc: doc.metadata.get("index", 0))
        for doc in sorted_docs:
            chunks.append({
                "index": doc.metadata.get("index", "N/A"),
                "text": doc.page_content
            })
        return jsonify(chunks)
    except Exception as e:
        print(f"'/manual_indexes' 처리 오류: {e}")
        return jsonify({"error": "인덱스 조회 중 오류 발생"}), 500


def setup_global_models_and_default():
    """
    서버 시작 로직 (RAM 로드 + Disk 저장 + .txt 파일 생성)
    (이 함수는 k=5를 적용하기 위해 create_rag.../load_rag...을 호출)
    """
    global llm, embeddings, global_pipelines, global_vector_stores, global_general_chain

    load_dotenv("a.env")
    if "GOOGLE_API_KEY" not in os.environ:
        print("GOOGLE_API_KEY 환경변수 없음")
        os._exit(1)

    try:
        # 님이 2.5 flash를 쓰신다고 했으니 gemini-1.5-flash
        llm = ChatGoogleGenerativeAI(model="gemini-2.5-flash")
        embeddings = GoogleGenerativeAIEmbeddings(model="models/text-embedding-004")
        print("Gemini 모델 초기화 완료.")
        global_general_chain = llm | StrOutputParser()
        print("일반 상담 체인 생성 완료.")
    except Exception as e:
        print(f"Gemini 모델 초기화 실패: {e}")
        os._exit(1)

    # 3. 기본 '병무청' 매뉴얼 로드
    manual_file = "예비군편성.txt"
    try:
        with open(manual_file, "r", encoding="utf-8") as f:
            manual_text = f.read()

        # k=5가 적용된 생성 함수 호출
        (chain, store) = create_rag_pipeline_for_manual(manual_text, llm, embeddings)
        global_pipelines["병무청"] = chain  # RAM에 저장
        global_vector_stores["병무청"] = store  # RAM에 저장
        print(f"기본 매뉴얼 '{manual_file}'을(를) '병무청' 이름으로 (RAM에) 로드 완료.")

        safe_folder_name = urllib.parse.quote_plus("병무청")
        index_path = os.path.join(VECTOR_STORE_DIR, f"{safe_folder_name}_index")
        if not os.path.exists(index_path):
            os.makedirs(VECTOR_STORE_DIR, exist_ok=True)
            store.save_local(index_path)
            print(f"기본 '병무청' 인덱스를 디스크({index_path})에 저장합니다.")

        # .txt 파일로 내보내기
        export_chunks_to_txt("병무청", store)

    except FileNotFoundError:
        print(f"경고: 기본 매뉴얼 '{manual_file}'을(를) 찾을 수 없습니다.")
    except Exception as e:
        print(f"기본 매뉴얼 로드 실패: {e}")

    # 4. 디스크(D:\rag_storage)에서 기존 기업 인덱스 스캔 및 로드
    print(f"디스크({VECTOR_STORE_DIR})에서 기존 기업 인덱스를 스캔합니다...")
    os.makedirs(VECTOR_STORE_DIR, exist_ok=True)

    for folder_name in os.listdir(VECTOR_STORE_DIR):
        if folder_name.endswith("_index"):
            safe_folder_name = folder_name.replace("_index", "")
            companyName = urllib.parse.unquote_plus(safe_folder_name)

            if companyName in global_pipelines:  # '병무청'은 이미 로드했으므로 통과
                continue

            print(f"'{companyName}' 인덱스를 디스크에서 로드합니다...")
            # k=5가 적용된 로드 함수 호출
            (chain, store) = load_rag_pipeline_from_disk(companyName, llm, embeddings)

            if chain and store:
                global_pipelines[companyName] = chain
                global_vector_stores[companyName] = store
                # .txt 파일로 내보내기
                export_chunks_to_txt(companyName, store)
            else:
                print(f"'{companyName}' 로드 실패.")

    print("모든 인덱스 로드 완료.")


if __name__ == '__main__':
    setup_global_models_and_default()
    from waitress import serve

    print(f"Waitress 서버를 0.0.0.0:5000에서 시작합니다 (영구 저장 지원).")
    serve(app, host='0.0.0.0', port=5000)