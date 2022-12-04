package db

import (
	"fmt"
	"log"
	"time"

	"github.com/jinzhu/gorm"
	_ "github.com/jinzhu/gorm/dialects/postgres"
)

var DB_HOST, DB_PORT, DB_USER, DB_NAME, DB_PWD string

func InitVar(host, port, user, name, pwd string) {
	DB_HOST, DB_PORT, DB_USER, DB_NAME, DB_PWD = host, port, user, name, pwd
}

func GetDatabase() *gorm.DB {

	var connection *gorm.DB
	var err error

	for {
		connection, err = gorm.Open("postgres", "host="+DB_HOST+" port="+DB_PORT+" user="+DB_USER+" sslmode=disable dbname="+DB_NAME+" password="+DB_PWD)

		print(DB_HOST, DB_NAME, DB_USER, DB_PWD, DB_PORT)

		if err != nil {
			log.Print("wrong database url", err, DB_HOST)
		} else {
			break
		}

		log.Print("Waiting for database ready")
		time.Sleep(time.Second * 10)
	}

	sqldb := connection.DB()

	err = sqldb.Ping()
	if err != nil {
		log.Fatal("database failed")
	}

	fmt.Println("connected to database")
	return connection
}

func Closedatabase(connection *gorm.DB) {
	sqldb := connection.DB()
	sqldb.Close()
}
