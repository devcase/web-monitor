package br.com.devcase.webmonitor.service;

import java.io.IOException;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import br.com.devcase.webmonitor.persistence.dao.MonitoredResourceDAO;
import br.com.devcase.webmonitor.persistence.domain.MonitoredResource;
import dwf.slack.SlackService;

@Service
public class CheckResourceServiceImpl implements CheckResourceService {
	private Log log = LogFactory.getLog(getClass());
	@Autowired
	private MonitoredResourceDAO monitoredResourceDAO;
	@Autowired(required=false)
	private SlackService slackService;
	@Value("${web-monitor.slackchannel:#general}")
	private String slackChannel;
	public String getSlackChannel() {
		return slackChannel;
	}
	public void setSlackChannel(String slackChannel) {
		this.slackChannel = slackChannel;
	}


	public void checkPending() throws IOException {
		List<MonitoredResource> pending = monitoredResourceDAO.findByFilter("pending", Boolean.TRUE);

		CloseableHttpClient hc = HttpClientBuilder.create().build();
		try {
			for (MonitoredResource monitoredResource : pending) {
				log.info("Checking health for: " + monitoredResource);
				monitoredResourceDAO.evict(monitoredResource);
				monitoredResource.setLastHealthCheck(new Date());
				
				HttpGet g = new HttpGet(monitoredResource.getHealthUrl());
				
				if(monitoredResource.getHealthCheckTimeout() != null) {
					RequestConfig reqConfig = RequestConfig.copy(g.getConfig()).setConnectTimeout(monitoredResource.getHealthCheckTimeout().intValue()).setSocketTimeout(monitoredResource.getHealthCheckTimeout().intValue()).build();
					g.setConfig(reqConfig);
				}
				CloseableHttpResponse response = hc.execute(g);
				
				Boolean previousCheckResult = monitoredResource.getHealthCheckResult();
				if(response.getStatusLine().getStatusCode() != HttpStatus.OK.value()) {
					//ERROR
					monitoredResource.setHealthCheckResult(Boolean.FALSE);
					monitoredResource.setNextHealthCheck(
							DateUtils.addMinutes(new Date(), monitoredResource.getHealthCheckPeriodOnError() != null
									? monitoredResource.getHealthCheckPeriodOnError() : 5));
					//notification
					if(previousCheckResult != Boolean.FALSE) {
						if(slackService != null) {
							slackService.postMessage(slackChannel, "Error detected for: " + monitoredResource);
						} else {
							log.debug("No slack service available");
						}
					}
				} else {
					log.info("Response got from service: " + response);
					
					//OK!
					monitoredResource.setHealthCheckResult(Boolean.TRUE);
					monitoredResource.setNextHealthCheck(
							DateUtils.addMinutes(new Date(), monitoredResource.getHealthCheckPeriod() != null
									? monitoredResource.getHealthCheckPeriod() : 60));
					
					//notification
					if(previousCheckResult == Boolean.FALSE) {
						if(slackService != null) {
							slackService.postMessage(slackChannel, "Back to normal: " + monitoredResource);
						} else {
							log.debug("No slack service available");
						}
					}

				}
				monitoredResourceDAO.updateByAnnotation(monitoredResource, MonitoredResource.HealthCheckUpdate.class);
				response.close();
			}
		} finally {
			hc.close();
		}
	}
}
