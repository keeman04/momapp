FROM nvidia/cuda:12.8.1-cudnn-runtime-ubuntu24.04
RUN apt-get update && apt-get install -y python3 python3-pip ffmpeg && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY requirements.txt .
RUN pip3 install --break-system-packages --no-cache-dir -r requirements.txt
COPY app.py .
ENV TANU_DEVICE=cuda TANU_COMPUTE_TYPE=float16 TANU_WHISPER_MODEL=small
EXPOSE 8080
CMD ["uvicorn","app:app","--host","0.0.0.0","--port","8080","--workers","1"]
