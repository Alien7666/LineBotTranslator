@echo off
REM Docker container packaging and pushing to Docker Hub batch file

REM Set Docker Hub username and image name
set DOCKER_USERNAME=your-dockerhub-username
set IMAGE_NAME=linebot-translator
set TAG=latest

echo Please enter your Docker Hub username:
set /p DOCKER_USERNAME=

echo Please enter image tag (default is latest):
set /p TAG_INPUT=
if not "%TAG_INPUT%"=="" set TAG=%TAG_INPUT%

echo.
echo The following settings will be used:
echo Docker Hub username: %DOCKER_USERNAME%
echo Image name: %IMAGE_NAME%
echo Tag: %TAG%
echo.

REM Login to Docker Hub
echo Logging in to Docker Hub...
docker login

REM Build image
echo Building Docker image...
docker build -t %DOCKER_USERNAME%/%IMAGE_NAME%:%TAG% .

REM Push to Docker Hub
echo Pushing image to Docker Hub...
docker push %DOCKER_USERNAME%/%IMAGE_NAME%:%TAG%

REM Create latest tag and push
if not "%TAG%"=="latest" (
    echo Creating and pushing latest tag...
    docker tag %DOCKER_USERNAME%/%IMAGE_NAME%:%TAG% %DOCKER_USERNAME%/%IMAGE_NAME%:latest
    docker push %DOCKER_USERNAME%/%IMAGE_NAME%:latest
)

echo.
echo Done! Image has been pushed to Docker Hub
echo You can use the following commands to pull and run the image on your server:
echo.
echo docker pull %DOCKER_USERNAME%/%IMAGE_NAME%:%TAG%
echo docker-compose up -d
echo.
echo Make sure you have docker-compose.yml and .env files on your server
echo.

pause
