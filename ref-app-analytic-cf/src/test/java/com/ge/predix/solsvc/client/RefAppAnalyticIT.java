package com.ge.predix.solsvc.client;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;

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
import com.ge.predix.solsvc.bootstrap.ams.factories.ModelFactory;
import com.ge.predix.solsvc.bootstrap.ams.factories.ModelFactoryImpl;
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
@SpringApplicationConfiguration(classes =
{
        FdhRouterApplication.class, TimeseriesGetDataHandler.class, RefAppAnalyticApplication.class
})
@WebAppConfiguration
@ImportResource("classpath:Test-ref-app-analytic-cf.xml")
/*
 * @ContextConfiguration(locations = {
 * "classpath:META-INF/spring/Test-ref-app-analytic-cf.xml" })
 */
@ActiveProfiles(
{
        "local", "timeseries"
})
@IntegrationTest(
{
        "server.port=9093"
})
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class RefAppAnalyticIT
{

    private static final Logger    log = LoggerFactory.getLogger(RefAppAnalyticIT.class);

    @Autowired
    private RestClient             restClient;

    @Autowired
    @Qualifier("defaultOauthRestConfig")
    private DefaultOauthRestConfig restConfig1;

    @Value("${predix.analytic.restProtocol}://${predix.analytic.restHost}:${predix.analytic.restPort}/${predix.analytic.restBaseResource}")
    private String                 refappAnalyticEndpoint;

    @Autowired
    @Qualifier("ModelFactory")
    private ModelFactoryImpl           modelFactory;

    @Autowired
    private AssetConfig            assetConfig;

    private List<Header>           assetHeaders;
    private List<Header>           timeseriesHeaders;

    /**
     * 
     */
    @Autowired
    protected TimeseriesClient     timeseriesClient;

    /**
     * -
     */
    @SuppressWarnings("nls")
    @Before
    public void setUp()
    {
        log.debug("Set up ");

        log.debug("URL in setup ...................." + this.refappAnalyticEndpoint);

        this.assetHeaders = setAssetHeaders();
    }

    /**
     * -
     * 
     * @throws IOException -
     */
    @SuppressWarnings("nls")
    @Test
    public void testAlarmThresholdsForMachine()
            throws IOException
    {
        log.debug("=======TEST WITH KNOWN SENSOR TAG NAME ================");

        testAnalytics(2, true, getRunAnalyticRequestSimpleTestDataFromFile());
        testAnalytics(29, false, getRunAnalyticRequestSimpleTestDataFromFile());
    }

    /**
     * -
     * 
     * @throws IOException -
     */
    @SuppressWarnings("nls")
    @Test
    public void testAlarmThresholdsForMachineUsingAssetModel()
            throws IOException
    {
        log.debug("=======TEST LOOKING UP SENSOR TAG NAME IN PREDIX ASSET ================");

        testAnalytics(12, true, getRunAnalyticRequestVariableTestDataFromFile());
        // testAnalytics(48, false, getRunAnalyticRequestVariableTestDataFromFile());
    }

    /**
     * -
     * 
     * @throws IOException -
     */
    @SuppressWarnings("nls")
    @Test
    public void testNormalWorkingMachineConditionWithoutKnowingSensor1()
            throws IOException
    {
        log.debug(
                "=======TEST LOOKUP SENSOR TAG IN PREDIX ASSET SENSOR=====but unknown sensor variable has illegal characters due to Analytics Runtime Bug ===========");

        testAnalytics(23, false, getRunAnalyticRequestVariableTestDataFromFile1());
        testAnalytics(22, true, getRunAnalyticRequestVariableTestDataFromFile1());
    }

    @SuppressWarnings("nls")
    private void testAnalytics(Integer actualDatapoint, Boolean actualAlertStatus, String analyticRequest)
            throws IOException
    {
        createDatapoint(actualDatapoint);
        setAlertStatus(this.assetHeaders, actualAlertStatus);

        log.debug("Request........................." + analyticRequest);

        CloseableHttpResponse httpResponse = null;
        try
        {
            httpResponse = this.restClient.post(this.refappAnalyticEndpoint, analyticRequest, this.assetHeaders);

            try
            {
                Thread.currentThread();
                Thread.sleep(5000);
            }
            catch (InterruptedException e)
            {
                log.error(e.getMessage(), e);
            }

            verifyResponse(httpResponse, this.assetHeaders, actualDatapoint, !actualAlertStatus);
        }
        finally
        {
            if ( httpResponse != null ) httpResponse.close();
        }

    }

    @SuppressWarnings("nls")
    private List<Header> setAssetHeaders()
    {

        List<Header> localHeaders = this.restClient.getSecureTokenForClientId();
        this.restClient.addZoneToHeaders(localHeaders, this.assetConfig.getZoneId());

        Header header = new BasicHeader("Content-Type", "application/json");
        localHeaders.add(header);
        header = new BasicHeader("Accept", "application/json");
        localHeaders.add(header);

        return localHeaders;
    }

    @SuppressWarnings("nls")
    private void setAlertStatus(List<Header> headers, Boolean status)
    {
        List<Object> models = this.modelFactory
                .getModels("/asset/compressor-2017.alert-status.crank-frame-discharge-pressure", "Asset", headers);

        ((Attribute) ((Asset) models.get(0)).getAttributes().get("alertStatus")).getValue().set(0, status);
        this.modelFactory.updateModel(models.get(0), "Asset", headers);
    }

    @SuppressWarnings("nls")
    private void verifyResponse(CloseableHttpResponse httpResponse, List<Header> headersArg,
            Integer expectedValueOfSensor, Boolean expectedAlertStatus)
    {

        log.debug("Response = " + httpResponse);
        String reply = this.restClient.getResponse(httpResponse);
        log.debug("Response = " + reply);

        Assert.assertEquals(httpResponse.getStatusLine().getStatusCode(), HttpStatus.SC_OK);
        Assert.assertTrue(reply, reply.contains("errorEvent\":[]"));

        List<Object> models = this.modelFactory
                .getModels("/asset/compressor-2017.alert-status.crank-frame-discharge-pressure", "Asset", headersArg);

        Assert.assertEquals(expectedAlertStatus,
                ((Attribute) ((Asset) models.get(0)).getAttributes().get("alertStatus")).getValue().get(0));

        if ( ((Attribute) ((Asset) models.get(0)).getAttributes().get("alertLevelValue")).getValue()
                .get(0) instanceof Integer )
        {
            Integer actualValueOfSensor = (Integer) ((Attribute) ((Asset) models.get(0)).getAttributes()
                    .get("alertLevelValue")).getValue().get(0);

            Assert.assertEquals(expectedValueOfSensor, actualValueOfSensor);
        }
        else
        {
            Double actualValueOfSensor = (Double) ((Attribute) ((Asset) models.get(0)).getAttributes()
                    .get("alertLevelValue")).getValue().get(0);

            Assert.assertEquals(expectedValueOfSensor, actualValueOfSensor);
        }

    }

    @SuppressWarnings("nls")
    private String getRunAnalyticRequestSimpleTestDataFromFile()
    {
        try
        {
            return IOUtils.toString(getClass().getClassLoader().getResourceAsStream("RunAnalyticRequest.json"));
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("nls")
    private String getRunAnalyticRequestVariableTestDataFromFile()
    {
        try
        {
            return IOUtils
                    .toString(getClass().getClassLoader().getResourceAsStream("RunAnalyticRequestWithVariables.json"));
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("nls")
    private String getRunAnalyticRequestVariableTestDataFromFile1()
    {
        try
        {
            return IOUtils.toString(
                    getClass().getClassLoader().getResourceAsStream("RunAnalyticRequestWithVariablesCommentTag.json"));
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("nls")
    private void createDatapoint(Integer actualValueOfSensor)
    {
        DatapointsIngestion dpIngestion = new DatapointsIngestion();
        dpIngestion.setMessageId(String.valueOf(System.currentTimeMillis()));

        List<Object> datapoint1 = new ArrayList<Object>();
        datapoint1.add(System.currentTimeMillis());
        datapoint1.add(actualValueOfSensor);
        datapoint1.add(3); // quality

        List<Object> datapoints = new ArrayList<Object>();
        datapoints.add(datapoint1);

        Body body = new Body();
        body.setName("Compressor-2017:DischargePressure");
        body.setDatapoints(datapoints);

        List<Body> bodies = new ArrayList<Body>();
        bodies.add(body);

        dpIngestion.setBody(bodies);

        this.timeseriesClient.createTimeseriesWebsocketConnectionPool();
        this.timeseriesClient.postDataToTimeseriesWebsocket(dpIngestion);

        queryForLatestDatapoints(actualValueOfSensor);
    }

    @SuppressWarnings("nls")
    private void queryForLatestDatapoints(Integer actualValueOfSensor)
    {
        boolean done = false;
        while (!done)
        {
            com.ge.predix.entity.timeseries.datapoints.queryrequest.latest.DatapointsLatestQuery datapoints = new com.ge.predix.entity.timeseries.datapoints.queryrequest.latest.DatapointsLatestQuery();
            com.ge.predix.entity.timeseries.datapoints.queryrequest.latest.Tag tag = new com.ge.predix.entity.timeseries.datapoints.queryrequest.latest.Tag();
            tag.setName("Compressor-2017:DischargePressure"); //$NON-NLS-1$

            List<com.ge.predix.entity.timeseries.datapoints.queryrequest.latest.Tag> tagList = new ArrayList<com.ge.predix.entity.timeseries.datapoints.queryrequest.latest.Tag>();
            tagList.add(tag);
            datapoints.setTags(tagList);
            this.timeseriesHeaders = this.timeseriesClient.getTimeseriesHeaders();
            com.ge.predix.entity.timeseries.datapoints.queryresponse.DatapointsResponse response = this.timeseriesClient
                    .queryForLatestDatapoint(datapoints, this.timeseriesHeaders);
            assertNotNull(response);
            try
            {
                assertEquals(((List<?>) response.getTags().get(0).getResults().get(0).getValues().get(0)).get(1),
                        actualValueOfSensor);
                done = true;
            }
            catch (AssertionError e)
            {
                try
                {
                    log.warn("timeseries value=" + actualValueOfSensor + " is not available yet, sleeping");
                    Thread.sleep(3000);
                }
                catch (InterruptedException e1)
                {
                    throw new RuntimeException(e1);
                }
            }

        }
    }

}
