import os
from flask import Flask, redirect, url_for, request, render_template, jsonify, request

app = Flask("LuceneSearch")


@app.route("/")
def index():
  
  return render_template("index.html")

if __name__ == "__main__":
  app.run(host="0.0.0.0", port=3000, debug=True)