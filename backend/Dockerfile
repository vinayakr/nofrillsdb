# Use the slim version of OpenJDK 17 based on Debian
FROM eclipse-temurin:21-jdk-alpine-3.23

# Create a volume at /tmp
VOLUME /tmp

# Add the JAR file to the container
ADD build/libs/nofrillsdb.jar nofrillsdb.jar

RUN apk add --no-cache tzdata
ENV TZ=America/Los_Angeles

# Copy AWS credentials
COPY credentials /root/.aws/credentials

# Copy the entrypoint script to the container
COPY entrypoint.sh entrypoint.sh

# Make the entrypoint script executable
RUN chmod +x entrypoint.sh

RUN apk add --no-cache netcat-openbsd

# Expose port 8443 for the application
EXPOSE 8080

# Define the entrypoint script as the container's entrypoint
ENTRYPOINT ["./entrypoint.sh"]

