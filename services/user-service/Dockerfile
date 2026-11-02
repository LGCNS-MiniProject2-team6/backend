FROM eclipse-temurin:17-jre
WORKDIR /app
COPY build/libs/user-service.jar app.jar
ENV JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -Duser.timezone=Asia/Seoul -XX:MaxRAMPercentage=75"
USER 10001:10001
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
