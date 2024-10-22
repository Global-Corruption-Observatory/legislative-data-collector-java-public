# Chrome & Chromedriver upgrade

## Chrome:
Install:
```
sudo apt-get update
sudo apt-get --only-upgrade install google-chrome-stable
```
Validate:
```
google-chrome --version
```

## Chromedriver
remove previous chromedriver:
```
sudo rm /usr/bin/chromedriver
```
download the stable chromedriver
- get the url from [here](https://googlechromelabs.github.io/chrome-for-testing/#stable)
- find Stable chromedriver for linux64 platform and copy the url
  download and unpack it:
```
wget https://storage.googleapis.com/chrome-for-testing-public/130.0.6723.58/linux64/chromedriver-linux64.zip
unzip chromedriver-linux64.zip
sudo cp chromdriver-linux64/chromedriver /usr/bin/
sudo chmod +x /usr/bin/chromedriver
```
Validate:
```
chromedriver --version
```