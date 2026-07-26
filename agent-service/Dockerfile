# Agent service. Python 3.12 slim; no build toolchain in the final image.
FROM python:3.12-slim AS base

# Non-root from the start. This process holds an HMS credential and reaches the
# hospital network; it has no reason to run as root.
RUN groupadd -r hms && useradd -r -g hms -d /app hms

WORKDIR /app
COPY pyproject.toml README.md ./
COPY src ./src
RUN pip install --no-cache-dir . && rm -rf /root/.cache

USER hms
EXPOSE 8090

# /ready reports missing configuration by name rather than just failing.
HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
  CMD python -c "import urllib.request,sys; \
      sys.exit(0 if urllib.request.urlopen('http://localhost:8090/health').status==200 else 1)"

CMD ["uvicorn", "hms_agent.app:create_app", "--factory", \
     "--host", "0.0.0.0", "--port", "8090", "--workers", "2"]
