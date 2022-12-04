package main

import (
	"context"
	"encoding/json"
	"log"
	"net/http"
	"os"
	"sender/db"
	"time"

	"github.com/gin-gonic/gin"
	amqp "github.com/rabbitmq/amqp091-go"
)

type Doc struct {
	Link        string `json:"link"`
	Title       string `json:"title"`
	Description string `json:"description"`
	Content     string `json:"content"`
}

type Link struct {
	Link string `json:"link"`
}

func failOnError(err error, msg string) {
	if err != nil {
		log.Panicf("%s: %s", msg, err)
	}
}

var RABBITMQ_HOST, RABBITMQ_USER, RABBITMQ_PWD string

func InitVarRabbitMQ(HOST, USER, PWD string) {
	RABBITMQ_HOST = HOST
	RABBITMQ_USER = USER
	RABBITMQ_PWD = PWD
}

func main() {

	// DATABASE SETUP

	db.InitVar(os.Getenv("POSTGRES_HOST"), os.Getenv("POSTGRES_PORT"), os.Getenv("POSTGRES_USER"), os.Getenv("POSTGRES_DB"), os.Getenv("POSTGRES_PWD"))
	connection := db.GetDatabase()
	defer db.Closedatabase(connection)

	// RABBITMQ SETTUP

	InitVarRabbitMQ(os.Getenv("RABBITMQ_HOST"), os.Getenv("RABBITMQ_USER"), os.Getenv("RABBITMQ_PWD"))

	print(RABBITMQ_HOST, RABBITMQ_USER, RABBITMQ_PWD)

	conn, err := amqp.Dial("amqp://group06:group06@rabbitmq:5672/")
	failOnError(err, "Failed to connect to RabbitMQ")
	defer conn.Close()

	ch, err := conn.Channel()
	failOnError(err, "Failed to open a channel")
	defer ch.Close()

	q, err := ch.QueueDeclare(
		"news", // name
		true,   // durable
		false,  // delete when unused
		false,  // exclusive
		false,  // no-wait
		nil,    // arguments
	)
	failOnError(err, "Failed to declare a queue")

	// API SETUP

	r := gin.Default()
	r.POST("/news", func(c *gin.Context) {
		var doc Doc

		err := c.ShouldBind(&doc)

		if err != nil {
			c.AbortWithStatusJSON(http.StatusBadRequest, err)
			return
		}

		var checkDoc Link

		connection.Where("link = ?", doc.Link).Find(&checkDoc)

		if checkDoc.Link != "" {
			c.JSON(http.StatusBadRequest, gin.H{"error": "This news has been received!"})
			return
		}

		result := connection.Exec("INSERT INTO LINKS VALUES(?)", doc.Link)
		if result.Error != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
			return
		}

		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()

		docJSON, err := json.Marshal(doc)

		if err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
			return
		}

		err = ch.PublishWithContext(ctx,
			"",     // exchange
			q.Name, // routing key
			false,  // mandatory
			false,  // immediate
			amqp.Publishing{
				ContentType: "application/json",
				Body:        docJSON,
			})

		if err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
			return
		}

		c.String(http.StatusAccepted, "Message is sent")
	})

	r.Run()
}
