# =============================================================================
# LUMIO – Academic Performance Platform
# Two-stage build: compile with a JDK, run on a smaller JRE image.
# Java 21 (LTS) is used for both stages; PDFBox 3.x needs Java 8 or newer.
# =============================================================================

# ---------- Stage 1: compile ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build

# Only the PDFBox jar your project uses (the same one as your local commands).
COPY lib/pdfbox-app-3.0.8.jar lib/pdfbox-app-3.0.8.jar
COPY Server.java GpaCalculator.java ./

# Same as your local compile command, but writing the .class files to ./classes
RUN mkdir classes && \
    javac -encoding UTF-8 -cp "lib/pdfbox-app-3.0.8.jar" -d classes Server.java GpaCalculator.java

# ---------- Stage 2: run ----------
FROM eclipse-temurin:21-jre
# The server looks for "public", "data" and "uploads" relative to the working directory.
WORKDIR /app

COPY --from=build /build/classes /app/classes
COPY lib/pdfbox-app-3.0.8.jar /app/lib/pdfbox-app-3.0.8.jar
COPY public/ /app/public/

# --- Configuration (read by Server.java) ---
# Render sets PORT itself (10000 by default); 8080 is only the fallback.
ENV PORT=8080
# History file location. To keep history across deploys, attach a Render disk and point this
# at the disk, e.g. LUMIO_DATA_DIR=/var/data/history  (see the deployment notes).
ENV LUMIO_DATA_DIR=/app/data
ENV LUMIO_UPLOAD_DIR=/app/uploads
# false = marksheets are read in memory and NOT stored (recommended for a public site, because
# visitors upload personal marksheets). Set to true to keep uploaded files.
ENV LUMIO_SAVE_UPLOADS=false

EXPOSE 8080

# Linux uses ":" (not ";") to separate classpath entries.
# MaxRAMPercentage lets the JVM size its heap from the container's memory limit.
CMD ["java", "-XX:MaxRAMPercentage=75", "-cp", "classes:lib/pdfbox-app-3.0.8.jar", "Server"]