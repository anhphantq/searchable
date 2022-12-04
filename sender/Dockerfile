FROM golang:latest

WORKDIR /app

COPY . .

RUN go mod download

RUN go build -o /sender

EXPOSE 8080

CMD [ "/sender" ]