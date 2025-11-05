import os
from dotenv import load_dotenv
from flask import Flask, request, jsonify

# LangChain 1.x 및 Google Generative AI 임포트
from langchain_google_genai import ChatGoogleGenerativeAI, GoogleGenerativeAIEmbeddings
from langchain_community.vectorstores import FAISS
from langchain_text_splitters import RecursiveCharacterTextSplitter
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.output_parsers import StrOutputParser
from langchain_core.runnables import RunnableParallel, RunnablePassthrough

app = Flask(__name__)
rag_chain = None  # 전역 RAG 체인

# 기본 루트 라우트 - 접속 확인용
@app.route('/', methods=['GET'])
def index():
    return "RAG 챗봇 서버가 실행 중입니다. POST /chat 경로로 질문을 전송하세요."

def setup_rag_pipeline():
    global rag_chain

    load_dotenv("a.env")  # .env에서 GOOGLE_API_KEY 로드

    if "GOOGLE_API_KEY" not in os.environ:
        print("GOOGLE_API_KEY 환경변수 없음")
        os._exit(1)

    # LLM과 임베딩 모델 초기화
    llm = ChatGoogleGenerativeAI(model="gemini-2.5-flash")
    embeddings = GoogleGenerativeAIEmbeddings(model="text-embedding-004")

    # 매뉴얼 파일 로드
    manual_file = "예비군편성.txt"
    with open(manual_file, "r", encoding="utf-8") as f:
        manual_text = f.read()

    # 텍스트 분할 및 문서 생성
    text_splitter = RecursiveCharacterTextSplitter(chunk_size=500, chunk_overlap=50)
    documents = text_splitter.create_documents([manual_text])

    # 벡터 스토어 생성하고 검색기 설정
    vector_store = FAISS.from_documents(documents, embeddings)
    retriever = vector_store.as_retriever(search_kwargs={"k": 3})

    # 프롬프트 템플릿
    prompt = ChatPromptTemplate.from_template("""
당신은 친절하고 정확한 AI 상담원입니다.
[참고 매뉴얼]: {context}
[질문]: {input}
[답변]:
""")

    # RAG 체인 생성
    rag_chain = (
        RunnableParallel({
            "context": retriever,
            "input": RunnablePassthrough(),
        }) | prompt | llm | StrOutputParser()
    )

@app.route('/chat', methods=['POST'])
def chat():
    global rag_chain
    if rag_chain is None:
        return jsonify({"error": "서버가 준비되지 않았습니다."}), 503

    data = request.json
    if not data or 'query' not in data:
        return jsonify({"error": "질문(query)이 누락되었습니다."}), 400

    user_query = data['query']
    answer = rag_chain.invoke(user_query)
    return jsonify({"answer": answer})

if __name__ == '__main__':
    setup_rag_pipeline()
    from waitress import serve
    serve(app, host='0.0.0.0', port=5000)
