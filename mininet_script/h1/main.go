package main

import (
	"fmt"
	"html/template"
	"io"
	"log"
	"net/http"

	"github.com/go-resty/resty/v2"
	"github.com/labstack/echo"
)

var database = map[string]string{
	"admin": "admin",
	"test":  "test",
}

type Template struct {
	templates *template.Template
}

func (t *Template) Render(w io.Writer, name string, data interface{}, c echo.Context) error {
	return t.templates.ExecuteTemplate(w, name, data)
}

func main() {
	t := &Template{
		templates: template.Must(template.ParseGlob("public/views/*.html")),
	}

	e := echo.New()
	e.Renderer = t
	e.File("/", "public/views/index.html")
	e.POST("/login", login)
	e.Logger.Fatal(e.Start(":80"))
}

func login(c echo.Context) error {

	username := c.FormValue("username")
	password := c.FormValue("password")
	log.Println(username)
	log.Println(password)
	fmt.Println(database)
	if storedPass, ok := database[username]; ok {
		if storedPass == password {
			authenticate(c.FormValue("macaddress"))
			return c.String(http.StatusOK, "Correctly Authenticated")
		}
	}
	return c.String(http.StatusForbidden, "Error invalid credentials")
}

func authenticate(mac string) {
	client := resty.New()
	_, err := client.R().
		SetHeader("Content-Type", "application/json").
		SetHeader("Authorization", "Basic b25vczpyb2Nrcw==").
		SetBody(`{"macAddress":"` + mac + `"}`).
		Post("http://10.0.0.4:8181/onos/authenticationportal/authenticateClient")
	if err != nil {
		log.Fatal(err)
	}
	log.Println("Posted")
}
