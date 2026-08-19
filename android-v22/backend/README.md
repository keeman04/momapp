# TANU Fast GPU backend

Deploy this behind HTTPS on a GPU machine. Set `TANU_SERVER_TOKEN` and terminate TLS at your reverse proxy/load balancer. The Android app sends 16-kHz mono PCM chunks as `application/octet-stream`. `faster-whisper` runs VAD and English translation and returns structured transcript segments.

Example:

```bash
docker build -t tanu-fast .
docker run --gpus all -p 8080:8080 -e TANU_SERVER_TOKEN='change-me' tanu-fast
```

Do not expose the raw container directly to the public internet without TLS/auth/rate limiting.
