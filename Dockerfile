FROM openjdk:14-jdk-alpine

RUN apk update && apk add tesseract-ocr leptonica

ENV JAR_FILE app.paperhands-assembly-0.1.0.jar

ADD ./target/scala-2.13/$JAR_FILE /app.paperhands.jar

ENTRYPOINT ["java", "-jar", "/app.paperhands.jar"]
