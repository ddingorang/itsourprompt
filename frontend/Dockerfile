# 이 파일을 레포의 frontend/Dockerfile 로 저장 (파일명은 반드시 "Dockerfile")
# nginx.conf도 같은 frontend/ 디렉터리에 함께 저장할 것
# Jenkinsfile.frontend의 "docker build ..." 가 frontend/ 디렉터리 안에서 이 파일을 찾음

# ---- Build stage ----
FROM node:20-alpine AS build
WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build
# Vite 빌드 결과물은 dist/ 폴더에 생성됨

# ---- Run stage ----
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
