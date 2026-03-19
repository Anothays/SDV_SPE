FROM --platform=linux/amd64 justb4/jmeter:latest

USER root

# Install Prometheus Backend Listener plugin
RUN mkdir -p /opt/apache-jmeter-5.6.3/lib/ext \
    && wget -q -O /opt/apache-jmeter-5.6.3/lib/ext/jmeter-prometheus-plugin.jar \
       "https://github.com/johrstrom/jmeter-prometheus-plugin/releases/download/0.7.1/jmeter-prometheus-plugin-0.7.1.jar"

