# 이 파일을 레포의 frontend/Dockerfile 로 저장 (파일명은 반드시 "Dockerfile")
# nginx.conf도 같은 frontend/ 디렉터리에 함께 저장할 것
# Jenkinsfile.frontend의 "docker build ..." 가 frontend/ 디렉터리 안에서 이 파일을 찾음

# ---- Build stage ----
FROM node:20-alpine AS build
WORKDIR /app

# 평소에는 --build-arg를 주지 않는다. 그러면 apiClient가 상대 경로 '/api'를 쓰고
# 페이지의 오리진·스킴을 물려받아 mixed content·CORS·쿠키 SameSite 문제가 모두 사라진다.
#
# 절대 URL(예: http://i15a505.p.ssafy.io/api)을 넣지 말 것 — HTTPS로 서비스되는 페이지에서
# http를 호출하면 브라우저가 mixed content로 차단한다(Postman은 차단하지 않아 정상으로 보인다).
# 백엔드가 정말 다른 오리진에 있을 때만 https:// 절대 URL로 주고, 그때는 백엔드의
# @CrossOrigin 허용 오리진과 allowCredentials, 쿠키 SameSite=None; Secure를 함께 맞춰야 한다.
#
# (Vite는 빌드 타임에 값을 정적 JS로 인라인하므로 ENV로 미리 넣어야 함)
ARG VITE_API_BASE_URL
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL

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
