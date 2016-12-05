package com.ge.predix.solsvc.client;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.message.BasicHeader;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mimosa.osacbmv3_3.OsacbmDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import com.ge.predix.entity.analytic.port.Port;
import com.ge.predix.entity.analytic.port.portidentifier.PortIdentifier;
import com.ge.predix.entity.analytic.runanalytic.RunAnalyticRequest;
import com.ge.predix.entity.asset.Asset;
import com.ge.predix.entity.assetfilter.AssetFilter;
import com.ge.predix.entity.field.fieldidentifier.FieldIdentifier;
import com.ge.predix.entity.field.fieldidentifier.FieldSourceEnum;
import com.ge.predix.entity.fieldidentifiervalue.FieldIdentifierValue;
import com.ge.predix.entity.fieldselection.FieldSelection;
import com.ge.predix.entity.filter.Filter;
import com.ge.predix.entity.model.Model;
import com.ge.predix.entity.timeseries.datapoints.ingestionrequest.Body;
import com.ge.predix.entity.timeseries.datapoints.ingestionrequest.DatapointsIngestion;
import com.ge.predix.entity.timeseries.datapoints.queryrequest.DatapointsQuery;
import com.ge.predix.entity.timeseriesfilter.TimeseriesFilter;
import com.ge.predix.solsvc.bootstrap.ams.common.AssetConfig;
import com.ge.predix.solsvc.bootstrap.ams.dto.Attribute;
import com.ge.predix.solsvc.bootstrap.ams.factories.ModelFactory;
import com.ge.predix.solsvc.ext.util.JsonMapper;
import com.ge.predix.solsvc.fdh.handler.timeseries.TimeseriesGetDataHandler;
import com.ge.predix.solsvc.fdh.router.boot.FdhRouterApplication;
import com.ge.predix.solsvc.refappanalytic.boot.RefAppAnalyticApplication;
import com.ge.predix.solsvc.restclient.impl.RestClient;
import com.ge.predix.solsvc.timeseries.bootstrap.factories.TimeseriesFactory;

/**
 * 
 * @author predix -
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = { FdhRouterApplication.class, TimeseriesGetDataHandler.class, RefAppAnalyticApplication.class })
@WebAppConfiguration
@ActiveProfiles({"local","timeseries"})
@IntegrationTest({ "server.port=9093" })
public class RefAppAnalyticIT {

	private static final Logger log = LoggerFactory.getLogger(RefAppAnalyticIT.class);

	@Autowired
	private RestClient restClient;

	@Value("${predix.analytic.restProtocol}://${predix.analytic.restHost}:${predix.analytic.restPort}/${predix.analytic.restBaseResource}")
	private String refappAnalyticEndpoint;


	@Autowired
	private JsonMapper jsonMapper;

	@Autowired
	private ModelFactory modelFactory;

	@Autowired
	private AssetConfig assetConfig;

	/**
	 * 
	 */
	@Autowired
	protected TimeseriesFactory timeseriesFactory;

	/**
	 * -
	 */
	@SuppressWarnings("nls")
	@Before
	public void setUp() {
		log.debug("Set up ");
	}

	/**
	 * -
	 */
	@SuppressWarnings("nls")
	@Test
	public void testRefAssetAnalytic() {
		log.debug("=======TEST REF ASSET Analytic================");
		createMetrics();

		RunAnalyticRequest request = getRunAnalyticRequestTestData();

		String analyticRequest = this.jsonMapper.toJson(request);
		log.debug("Request........................." + analyticRequest);

		log.debug("URL ...................." + this.refappAnalyticEndpoint);

		List<Header> headers = this.restClient.getSecureTokenForClientId();
		this.restClient.addZoneToHeaders(headers, this.assetConfig.getZoneId());

		Header header = new BasicHeader("Content-Type", "application/json");
		headers.add(header);
		header = new BasicHeader("Accept", "application/json");
		headers.add(header);

		this.jsonMapper.addSubtype(Asset.class);
		List<Model> models = this.modelFactory
				.getModels("/asset/compressor-2015.tag-extensions.crank-frame-discharge-pressure", "Asset", headers);
		((Attribute) ((Asset) models.get(0)).getAttributes().get("alertStatus")).getValue().set(0, true);
		this.modelFactory.updateModel(models.get(0), "Asset", headers);

		@SuppressWarnings("resource")
		CloseableHttpResponse httpResponse = this.restClient.post(this.refappAnalyticEndpoint, analyticRequest, headers);

		log.debug("Response = " + httpResponse);
		String reply = this.restClient.getResponse(httpResponse);
		log.debug("Response = " + reply);

		Assert.assertEquals(httpResponse.getStatusLine().getStatusCode(), HttpStatus.SC_OK);
		Assert.assertTrue(reply, reply.contains("errorEvent\":[]"));

		models = this.modelFactory.getModels("/asset/compressor-2015.tag-extensions.crank-frame-discharge-pressure",
				"Asset", headers);
		Assert.assertFalse(
				(Boolean) ((Attribute) ((Asset) models.get(0)).getAttributes().get("alertStatus")).getValue().get(0));
		log.debug(models.toString());
	}

	@SuppressWarnings("nls")
	private void createMetrics() {
		// String startTime = "2015-08-01 11:00:00";
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
		LocalDateTime startTime = LocalDateTime.parse("2015-08-01 18:00", formatter);
		long startTimeMillis = startTime.toInstant(ZoneOffset.UTC).getEpochSecond() * 1000;

		for (int i = 1; i < 10; i++) {
			DatapointsIngestion dpIngestion = new DatapointsIngestion();
			dpIngestion.setMessageId(String.valueOf(System.currentTimeMillis()));

			Body body = new Body();
			body.setName("Compressor-2015:DischargePressure");
			List<Object> datapoint1 = new ArrayList<Object>();
			datapoint1.add(startTimeMillis + (i * 1000));
			datapoint1.add(10);
			datapoint1.add(3); // quality

			List<Object> datapoints = new ArrayList<Object>();
			datapoints.add(datapoint1);

			body.setDatapoints(datapoints);

			List<Body> bodies = new ArrayList<Body>();
			bodies.add(body);

			dpIngestion.setBody(bodies);

			this.timeseriesFactory.createConnectionToTimeseriesWebsocket();
			this.timeseriesFactory.postDataToTimeseriesWebsocket(dpIngestion);

		}
	}

	@SuppressWarnings("nls")
	private RunAnalyticRequest getRunAnalyticRequestTestData() {
		RunAnalyticRequest request = new RunAnalyticRequest();

		// Input Ports
		// =============================

		// SELECT Clause
		// Input Port No.1
		String port1PortId = "ALARM_HI";
		String port1PortName = "ALARM_HI";
		String port1Field = "/asset/assetTag/crank-frame-dischargepressure/outputMaximum";
		String port1ExpectedDataType = OsacbmDataType.DM_REAL.value();
		String port1FieldSource = FieldSourceEnum.PREDIX_ASSET.name();
		String assetUriName = "/asset/assetId";
		String assetUriValue = "/asset/compressor-2015";

		// create Filter to look for an asset by id
		FieldIdentifierValue assetIdfieldIdentifierValue = new FieldIdentifierValue();
		FieldIdentifier assetIdFieldId = new FieldIdentifier();
		assetIdFieldId.setId(assetUriName);
		assetIdFieldId.setSource(port1FieldSource);
		assetIdfieldIdentifierValue.setFieldIdentifier(assetIdFieldId);
		assetIdfieldIdentifierValue.setValue(assetUriValue);
		
		AssetFilter assetFilter = new AssetFilter();
		assetFilter.setUri(assetUriValue);

		Port velocityHiPort = createPort(port1PortId, port1PortName, port1Field, port1FieldSource,
				port1ExpectedDataType, assetFilter);

		// Input Port No. 2
		String port2PortId = "ALARM_LO";
		String port2PortName = "ALARM_LO";
		String port2Field = "/asset/assetTag/crank-frame-dischargepressure/outputMinimum";
		String port2ExpectedDataType = OsacbmDataType.DM_REAL.value();
		String port2FieldSource = FieldSourceEnum.PREDIX_ASSET.name();
		// reuse the asset id Filter
		Port velocityLoPort = createPort(port2PortId, port2PortName, port2Field, port2FieldSource,
				port2ExpectedDataType, assetFilter);

		// Input Port No. 3
		String port3PortId = "TS_DATA";
		String port3PortName = "TS_DATA";
		String port3Field = "/asset/assetTag/crank-frame-dischargepressure";
		String port3ExpectedDataType = OsacbmDataType.DM_DATA_SEQ.value();
		String port3FieldSource = FieldSourceEnum.PREDIX_TIMESERIES.name();

		// create Filter to look for an asset by id and also add
		// startTime and endTime (TODO: change this to a new type of
		// Filter with better semantics)
		String startTimeField = "startTime";
		String startTime = "2015-08-01 11:00:00";
		String endTimeField = "endTime";
		String endTimeValue = "2015-08-08 23:00:00";
		Filter tsFieldFilter = getTimeseriesFieldFilter(assetUriName, assetUriValue, startTimeField, startTime,
				endTimeField, endTimeValue);
		Port velocityPort = createPort(port3PortId, port3PortName, port3Field, port3FieldSource, port3ExpectedDataType,
				tsFieldFilter);

		// Add Input Ports to Analytic request
		request.getInputPort().add(velocityHiPort);
		request.getInputPort().add(velocityLoPort);
		request.getInputPort().add(velocityPort);

		// ===========================================================================================================

		// Output Ports
		// =============================
		// Output Port No. 1
		String outputPort1PortId = "ALARM_STATUS";
		String outputPort1PortName = "ALARM_STATUS";
		String outputPort1Field = "/asset/assetTag/crank-frame-dischargepressure/tagDatasource/tagExtensions/attributes/alertStatus/value";
		String outputPort1FieldSource = FieldSourceEnum.PREDIX_ASSET.name();
		Port alarmStatusPort = createPort(outputPort1PortId, outputPort1PortName, outputPort1Field,
				outputPort1FieldSource, null, assetFilter);

		// Output Port No. 2
		String outputPort2PortId = "ALARM_LEVEL";
		String outputPort2PortName = "ALARM_LEVEL";
		String outputPort2Field = "/asset/assetTag/crank-frame-dischargepressure/tagDatasource/tagExtensions/attributes/alertLevel/value";
		String outputPort2FieldSource = FieldSourceEnum.PREDIX_ASSET.name();
		Port alarmLevelPort = createPort(outputPort2PortId, outputPort2PortName, outputPort2Field,
				outputPort2FieldSource, null, assetFilter);

		// Output Port No. 3
		String outputPort3PortId = "ALARM_LEVEL_VALUE";
		String outputPort3PortName = "ALARM_LEVEL_VALUE";
		String outputPort3Field = "/asset/assetTag/crank-frame-dischargepressure/tagDatasource/tagExtensions/attributes/alertLevelValue/value";
		String outputPort3FieldSource = FieldSourceEnum.PREDIX_ASSET.name();
		Port alarmLevelValuePort = createPort(outputPort3PortId, outputPort3PortName, outputPort3Field,
				outputPort3FieldSource, null, assetFilter);

		// Output Port No. 4
		String outputPort4PortId = "ALARM_LEVEL_VALUE_TIME";
		String outputPort4PortName = "ALARM_LEVEL_VALUE_TIME";
		String outputPort4Field = "/asset/assetTag/crank-frame-dischargepressure/tagDatasource/tagExtensions/attributes/alertTime/value";
		String outputPort4FieldSource = FieldSourceEnum.PREDIX_ASSET.name();
		Port alarmLevelValueTimePort = createPort(outputPort4PortId, outputPort4PortName, outputPort4Field,
				outputPort4FieldSource, null, assetFilter);

		// Output Port No. 5
		String outputPort5PortId = "ALARM_THRESHOLDDIFF";
		String outputPort5PortName = "ALARM_THRESHOLDDIFF";
		String outputPort5Field = "/asset/assetTag/crank-frame-dischargepressure/tagDatasource/tagExtensions/attributes/deltaThreshold/value";
		String outputPort5FieldSource = FieldSourceEnum.PREDIX_ASSET.name();
		Port alarmThresholdPort = createPort(outputPort5PortId, outputPort5PortName, outputPort5Field,
				outputPort5FieldSource, null, assetFilter);

		// Output Port No. 6
		String outputPort6PortId = "ALARM_THRESHOLDLEVEL";
		String outputPort6PortName = "ALARM_THRESHOLDLEVEL";
		String outputPort6Field = "/asset/assetTag/crank-frame-dischargepressure/tagDatasource/tagExtensions/attributes/deltaThresholdLevel/value";
		String outputPort6FieldSource = FieldSourceEnum.PREDIX_ASSET.name();
		Port alarmThresholdLevelPort = createPort(outputPort6PortId, outputPort6PortName, outputPort6Field,
				outputPort6FieldSource, null, assetFilter);

		// Add output ports
		request.getOutputPort().add(alarmStatusPort);
		request.getOutputPort().add(alarmLevelPort);
		request.getOutputPort().add(alarmLevelValuePort);
		request.getOutputPort().add(alarmLevelValueTimePort);
		request.getOutputPort().add(alarmThresholdPort);
		request.getOutputPort().add(alarmThresholdLevelPort);
		request.setExternalAttributeMap(null);

		return request;
	}

	/**
	 * @param assetUriField
	 * @param assetUriValue
	 * @param startTimeField
	 * @param startTime
	 * @param endTimeField
	 * @param endTimeValue
	 * @return -
	 */
	@SuppressWarnings("nls")
	private Filter getTimeseriesFieldFilter(String assetUriField, String assetUriValue, String startTimeField,
			String startTime, String endTimeField, String endTimeValue) {

		TimeseriesFilter tsFilter = new TimeseriesFilter();						
		
		// SET Time Series Filter
		DatapointsQuery query = new DatapointsQuery();
		query.setStart("5d-ago");
		query.setEnd(null);
		com.ge.predix.entity.timeseries.datapoints.queryrequest.Tag tag = new com.ge.predix.entity.timeseries.datapoints.queryrequest.Tag();
		tag.setName("Compressor-2015:DischargePressure");
		List<com.ge.predix.entity.timeseries.datapoints.queryrequest.Tag> tags = new ArrayList<com.ge.predix.entity.timeseries.datapoints.queryrequest.Tag>();
		tags.add(tag);
		query.setTags(tags);
		
		tsFilter.setDatapointsQuery(query);
		
		
		return tsFilter;
	}

	/**
	 * @param portId
	 * @param portName
	 * @param field
	 * @param fieldSource2
	 * @param assetIdFilter
	 * @return -
	 */
	private Port createPort(String portId, String portName, String field, String fieldSource, String expectedDataType,
			Filter filter) {
		Port aPort = new Port();
		PortIdentifier inputPortId = new PortIdentifier();
		inputPortId.setId(portId);
		inputPortId.setName(portName);

		// logical
		aPort.setPortIdentifier(inputPortId);
		// physical
		FieldSelection fieldSelection = new FieldSelection();
		FieldIdentifier fieldIdentifier = new FieldIdentifier();
		fieldIdentifier.setId(field);
		fieldIdentifier.setName(field);
		fieldIdentifier.setSource(fieldSource);
		fieldSelection.setFieldIdentifier(fieldIdentifier);
		fieldSelection.setExpectedDataType(expectedDataType);
		fieldSelection.setResultId(portId);

		aPort.setFieldSelection(fieldSelection);

		// physical filter where clause
		aPort.setFilter(filter);

		return aPort;
	}
}
