package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io/ioutil"
	"net/http"
	"os"
	"regexp"
	"time"

	"github.com/PuerkitoBio/goquery"
	"github.com/gocolly/colly/v2"
)

type Pages struct {
	Pages []Page `json:"pages"`
}

type Page struct {
	Link       string     `json:"link"`
	Navigation Navigation `json:"navigation"`
	Category   Category   `json:"category"`
	Document   Document   `json:"document"`
}

type Document struct {
	Query       string       `json:"query"`
	Validatios  []Validation `json:"validations"`
	Title       Title        `json:"title"`
	Description Description  `json:"description"`
	Content     Content      `json:"content"`
}

type Title struct {
	Query       string       `json:"query"`
	Validations []Validation `json:"validations"`
}

type Content struct {
	Query       string       `json:"query"`
	Validations []Validation `json:"validations"`
}

type Description struct {
	Query       string       `json:"query"`
	Validations []Validation `json:"validations"`
}

type Navigation struct {
	Query       string       `json:"query"`
	Validations []Validation `json:"validations"`
}

type Category struct {
	Query       string       `json:"query"`
	Validations []Validation `json:"validations"`
	Paging      Paging       `json:"paging"`
}

type Paging struct {
	Format string `json:"format"`
	From   int    `json:"from"`
	To     int    `json:"to"`
}

type Validation struct {
	Name  string `json:"name"`
	Regex string `json:"regex"`
}

type Doc struct {
	Link        string
	Title       string
	Description string
	Content     string
}

type void struct{}

var member void

var PagesToCrawl []Page

var SENDER_URL string

var Client http.Client

func docsToScrape(page Page) {
	categoryUrls := make([]string, 0)

	collector := colly.NewCollector()

	collector.OnHTML(page.Navigation.Query, func(e *colly.HTMLElement) {
		for _, rex := range page.Navigation.Validations {
			matched, err := regexp.MatchString(rex.Regex, e.Attr(rex.Name))

			if err != nil {
				panic("Something went wrong when validating element")
			}

			if !matched {
				return
			}
		}

		categoryUrls = append(categoryUrls, e.Attr("href"))
	})

	// collector.OnRequest(func(request *colly.Request) {
	// 	fmt.Println("Visiting", request.URL.String())
	// })

	collector.Visit(page.Link)

	// for i := 0; i < len(categoryUrls); i++ {
	// 	fmt.Println(categoryUrls[i])
	// }

	docsUrls := make(map[string]void)

	collector = colly.NewCollector()

	collector.OnHTML(page.Category.Query, func(e *colly.HTMLElement) {
		for _, rex := range page.Category.Validations {
			matched, err := regexp.MatchString(rex.Regex, e.Attr(rex.Name))

			if err != nil {
				panic("Something went wrong when validating element")
			}

			if !matched {
				return
			}
		}

		docsUrls[e.Attr("href")] = member
	})

	// collector.OnRequest(func(request *colly.Request) {
	// 	fmt.Println("Visiting", request.URL.String())
	// })

	for _, url := range categoryUrls {
		matched, err := regexp.MatchString(page.Link, url)

		if err != nil {
			panic("Something went wrong when validating element")
		}

		if !matched {
			url = page.Link + url
		}

		if url[len(url)-4:] == ".htm" {
			url = url[:len(url)-4]
		}

		if url[len(url)-5:] == ".html" {
			url = url[:len(url)-5]
		}

		for i := page.Category.Paging.From; i <= page.Category.Paging.To; i++ {
			collector.Visit(url + fmt.Sprintf(page.Category.Paging.Format, i))
		}
	}

	collector.Visit(page.Link)

	collector = colly.NewCollector()

	collector.OnResponse(func(r *colly.Response) {
		if r.StatusCode != 200 {
			println("Cannot get ", r.Request.URL)
			return
		}

		println(r.Request.URL.String())

		if r.Body == nil {
			return
		}

		doc, err := goquery.NewDocumentFromReader(bytes.NewReader(r.Body))

		if err != nil {
			println("Cannot get get body")
			return
		}

		document := Doc{Link: r.Request.URL.String()}

		title := doc.Find(page.Document.Title.Query).First()

		for _, rex := range page.Document.Title.Validations {

			attr, exist := title.Attr(rex.Name)

			if !exist {
				return
			}

			matched, err := regexp.MatchString(rex.Regex, attr)

			if err != nil {
				panic("Something went wrong when validating title")
			}

			if !matched {
				return
			}
		}

		document.Title = title.Text()

		description := doc.Find(page.Document.Description.Query)

		for _, rex := range page.Document.Description.Validations {

			attr, exist := description.Attr(rex.Name)

			if !exist {
				return
			}

			matched, err := regexp.MatchString(rex.Regex, attr)

			if err != nil {
				panic("Something went wrong when validating title")
			}

			if !matched {
				return
			}
		}

		document.Description = description.Text()

		doc.Find(page.Document.Content.Query).Each(func(i int, s *goquery.Selection) {
			for _, rex := range page.Document.Content.Validations {

				attr, exist := s.Attr(rex.Name)

				if !exist {
					return
				}

				matched, err := regexp.MatchString(rex.Regex, attr)

				if err != nil {
					panic("Something went wrong when validating title")
				}

				if !matched {
					return
				}
			}

			document.Content += s.Text()
		})

		body, err := json.Marshal(document)

		if err != nil {
			return
		}

		req, err := http.NewRequest("POST", SENDER_URL, bytes.NewBuffer(body))

		if err != nil {
			return
		}

		req.Header.Add("Content-Type", "application/json")

		res, err := Client.Do(req)

		if err != nil {
			fmt.Println(err)
			fmt.Println(req.URL, req.Header, req.Method)
		}

		fmt.Println(res.StatusCode, res.Header)
	})

	for url := range docsUrls {
		matched, err := regexp.MatchString(page.Link, url)

		if err != nil {
			panic("Something went wrong when validating element")
		}

		if !matched {
			url = page.Link + url
		}

		collector.Visit(url)
	}
}

func configInit() {
	var pages Pages

	configFile, err := os.Open("config copy 2.json")

	if err != nil {
		fmt.Println(err)
	}

	byteValue, _ := ioutil.ReadAll(configFile)

	json.Unmarshal(byteValue, &pages)

	PagesToCrawl = pages.Pages

	defer configFile.Close()
}

func main() {
	time.Sleep(time.Second * 20)

	configInit()

	SENDER_URL = os.Getenv("SENDER_URL")

	println(SENDER_URL)

	Client = http.Client{
		Timeout: 2 * time.Second,
	}

	for {
		for i := 0; i < len(PagesToCrawl); i++ {
			fmt.Println("Starting crawling ", PagesToCrawl[i].Link)
			docsToScrape(PagesToCrawl[i])
		}

		time.Sleep(time.Hour)
	}
}
