package com.ge.predix.solsvc.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.cxf.helpers.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.message.BasicHeader;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.context.annotation.ImportResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import com.ge.predix.entity.asset.Asset;
import com.ge.predix.entity.timeseries.datapoints.ingestionrequest.Body;
import com.ge.predix.entity.timeseries.datapoints.ingestionrequest.DatapointsIngestion;
import com.ge.predix.solsvc.bootstrap.ams.common.AssetConfig;
import com.ge.predix.solsvc.bootstrap.ams.dto.Attribute;
import com.ge.predix.solsvc.bootstrap.ams.factories.AssetClientImpl;
import com.ge.predix.solsvc.fdh.handler.timeseries.TimeseriesGetDataHandler;
import com.ge.predix.solsvc.fdh.router.boot.FdhRouterApplication;
import com.ge.predix.solsvc.refappanalytic.boot.RefAppAnalyticApplication;
import com.ge.predix.solsvc.restclient.config.DefaultOauthRestConfig;
import com.ge.predix.solsvc.restclient.impl.RestClient;
import com.ge.predix.solsvc.timeseries.bootstrap.client.TimeseriesClient;

/**
 * 
 * @author predix -
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = { FdhRouterApplication.class, TimeseriesGetDataHandler.class,
		RefAppAnalyticApplication.class })
@WebAppConfiguration
@ImportResource("classpath:Test-ref-app-analytic-cf.xml")
/*
 * @ContextConfiguration(locations = {
 * "classpath:META-INF/spring/Test-ref-app-analytic-cf.xml" })
 */
@ActiveProfiles({ "local", "asset", "timeseries" })
@IntegrationTest({ "server.port=9093" })
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class RefAppAnalyticIT {

	private static final Logger log = LoggerFactory.getLogger(RefAppAnalyticIT.class);

	@Autowired
	private RestClient restClient;

	@Autowired
	@Qualifier("defaultOauthRestConfig")
	private DefaultOauthRestConfig restConfig1;

	@Value("${predix.analytic.restProtocol}://${predix.analytic.restHost}:${predix.analytic.restPort}/${predix.analytic.restBaseResource}")
	private String refappAnalyticEndpoint;

	@Autowired
	@Qualifier("AssetClient")
	private AssetClientImpl assetClient;

	@Autowired
	private AssetConfig assetConfig;

	private List<Header> assetHeaders;
	private String timeseriesTag;

	/**
	 * 
	 */
	@Autowired
	protected TimeseriesClient timeseriesClient;

	/**
	 * -
	 */
	@SuppressWarnings("nls")
	@Before
	public void setUp() {
		log.debug("Set up ");

		log.debug("URL in setup ...................." + this.refappAnalyticEndpoint);

		this.assetHeaders = setAssetHeaders();
		this.timeseriesTag = "Machine-103:CompressionRatio";
		this.timeseriesClient.createTimeseriesWebsocketConnectionPool();

	}

	/**
	 * -
	 * 
	 * @throws IOException
	 *             -
	 */
	@SuppressWarnings("nls")
	@Test
	public void testAlarmThresholdsForMachine() throws IOException {
		log.debug("=======TEST WITH KNOWN SENSOR TAG NAME ================");

		testAnalytics(2, true, getRunAnalyticRequestSimpleTestDataFromFile());
		// testAnalytics(29, false,
		// getRunAnalyticRequestSimpleTestDataFromFile());
		// sleep();
	}

	/**
	 * -
	 * 
	 * @throws IOException
	 *             -
	 */
	@SuppressWarnings("nls")
	@Test
	public void testAlarmThresholdsForMachineUsingAssetModel() throws IOException {
		log.debug("=======TEST LOOKING UP SENSOR TAG NAME IN PREDIX ASSET ================");

		testAnalytics(12, true, getRunAnalyticRequestVariableTestDataFromFile());
		// testAnalytics(48, false,
		// getRunAnalyticRequestVariableTestDataFromFile());
		// sleep();
	}

	/**
	 * -
	 * 
	 * @throws IOException
	 *             -
	 */
	@SuppressWarnings("nls")
	@Test
	public void testNormalWorkingMachineConditionWithoutKnowingSensor1() throws IOException {
		log.debug(
				"=======TEST LOOKUP SENSOR TAG IN PREDIX ASSET SENSOR=====but unknown sensor variable has illegal characters due to Analytics Runtime Bug ===========");

		testAnalytics(23, false, getRunAnalyticRequestVariableTestDataFromFile1());
		// testAnalytics(22, true,
		// getRunAnalyticRequestVariableTestDataFromFile1());
		// sleep();
	}

	@SuppressWarnings("nls")
	private void testAnalytics(Integer actualDatapoint, Boolean actualAlertStatus, String analyticRequest)
			throws IOException {

		createAsset();
		sleep();
		createDatapoint(this.timeseriesTag, actualDatapoint);
		sleep();
		setAlertStatus(actualAlertStatus);

		log.debug("Request........................." + analyticRequest);

		CloseableHttpResponse httpResponse = null;
		try {
			httpResponse = this.restClient.post(this.refappAnalyticEndpoint, analyticRequest, this.assetHeaders);
			sleep();
			verifyResponse(httpResponse, this.assetHeaders, actualDatapoint, !actualAlertStatus);
		} finally {
			if (httpResponse != null)
				httpResponse.close();
		}

	}

	private void sleep() {
		try {
			Thread.currentThread();
			log.debug("Sleep 1 seconds"); //$NON-NLS-1$
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			log.error(e.getMessage(), e);
		}
	}

	@SuppressWarnings("nls")
	private List<Header> setAssetHeaders() {

		List<Header> localHeaders = this.restClient.getSecureTokenForClientId();
		this.restClient.addZoneToHeaders(localHeaders, this.assetConfig.getZoneId());

		Header header = new BasicHeader("Content-Type", "application/json");
		localHeaders.add(header);
		header = new BasicHeader("Accept", "application/json");
		localHeaders.add(header);

		return localHeaders;
	}

	@SuppressWarnings("nls")
	private void setAlertStatus(Boolean status) {
		List<Object> models = this.assetClient.getModels("/asset/machine103.alert-status.compressionratio", "Asset",
				this.assetHeaders);

		((Attribute) ((Asset) models.get(0)).getAttributes().get("alertStatus")).getValue().set(0, status);
		this.assetClient.updateModel(models.get(0), "Asset", this.assetHeaders);
	}

	@SuppressWarnings("nls")
	private void verifyResponse(CloseableHttpResponse httpResponse, List<Header> headersArg,
			Integer expectedValueOfSensor, Boolean expectedAlertStatus) {

		log.debug("Response = " + httpResponse);
		String reply = this.restClient.getResponse(httpResponse);
		log.debug("Response = " + reply);

		Assert.assertEquals(httpResponse.getStatusLine().getStatusCode(), HttpStatus.SC_OK);
		Assert.assertTrue(reply, reply.contains("errorEvent\":[]"));

		List<Object> models = this.assetClient.getModels("/asset/machine103.alert-status.compressionratio", "Asset",
				headersArg);

		Assert.assertEquals(expectedAlertStatus,
				((Attribute) ((Asset) models.get(0)).getAttributes().get("alertStatus")).getValue().get(0));

		if (((Attribute) ((Asset) models.get(0)).getAttributes().get("alertLevelValue")).getValue()
				.get(0) instanceof Integer) {
			Integer actualValueOfSensor = (Integer) ((Attribute) ((Asset) models.get(0)).getAttributes()
					.get("alertLevelValue")).getValue().get(0);

			Assert.assertEquals(expectedValueOfSensor, actualValueOfSensor);
		} else {
			Double actualValueOfSensor = (Double) ((Attribute) ((Asset) models.get(0)).getAttributes()
					.get("alertLevelValue")).getValue().get(0);

			Assert.assertEquals(expectedValueOfSensor, actualValueOfSensor);
		}

	}

	@SuppressWarnings("nls")
	private String getRunAnalyticRequestSimpleTestDataFromFile() {
		try {
			return IOUtils.toString(getClass().getClassLoader().getResourceAsStream("RunAnalyticRequest.json"));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@SuppressWarnings("nls")
	private String getRunAnalyticRequestVariableTestDataFromFile() {
		try {
			return IOUtils
					.toString(getClass().getClassLoader().getResourceAsStream("RunAnalyticRequestWithVariables.json"));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@SuppressWarnings("nls")
	private String getRunAnalyticRequestVariableTestDataFromFile1() {
		try {
			return IOUtils.toString(
					getClass().getClassLoader().getResourceAsStream("RunAnalyticRequestWithVariablesCommentTag.json"));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@SuppressWarnings("nls")
	private String readAssetInfoFromFile() {
		try {
			return IOUtils.toString(getClass().getClassLoader().getResourceAsStream("Machine103.json"));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void createDatapoint(String tagName, Integer actualValueOfSensor) {
		DatapointsIngestion dpIngestion = new DatapointsIngestion();
		dpIngestion.setMessageId(String.valueOf(System.currentTimeMillis()));

		List<Object> datapoint1 = new ArrayList<Object>();
		// datapoint1.add(System.currentTimeMillis() + 100000000);
		datapoint1.add(System.currentTimeMillis());
		datapoint1.add(actualValueOfSensor);
		datapoint1.add(3); // quality

		List<Object> datapoints = new ArrayList<Object>();
		datapoints.add(datapoint1);

		Body body = new Body();
		body.setName(tagName);
		body.setDatapoints(datapoints);

		List<Body> bodies = new ArrayList<Body>();
		bodies.add(body);

		dpIngestion.setBody(bodies);

		this.timeseriesClient.postDataToTimeseriesWebsocket(dpIngestion);
	}

	@SuppressWarnings("nls")
	private void createAsset() {
		this.assetClient.createFromJson("/asset", readAssetInfoFromFile(), this.assetHeaders);
	}
}
