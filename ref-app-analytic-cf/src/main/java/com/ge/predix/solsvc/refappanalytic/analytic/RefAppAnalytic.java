package com.ge.predix.solsvc.refappanalytic.analytic;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.message.BasicHeader;
import org.mimosa.osacbmv3_3.DAString;
import org.mimosa.osacbmv3_3.DMBool;
import org.mimosa.osacbmv3_3.DMDataSeq;
import org.mimosa.osacbmv3_3.DMReal;
import org.mimosa.osacbmv3_3.DataEvent;
import org.mimosa.osacbmv3_3.NumAlert;
import org.mimosa.osacbmv3_3.OsacbmTime;
import org.mimosa.osacbmv3_3.SDDataEvent;
import org.mimosa.osacbmv3_3.SDTestReal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.ge.predix.entity.analytic.port.Port;
import com.ge.predix.entity.analytic.port.portidentifier.PortIdentifier;
import com.ge.predix.entity.analytic.runanalytic.RunAnalyticRequest;
import com.ge.predix.entity.analytic.runanalytic.RunAnalyticResult;
import com.ge.predix.entity.field.Field;
import com.ge.predix.entity.fielddata.FieldData;
import com.ge.predix.entity.fielddata.OsaData;
import com.ge.predix.entity.fielddatacriteria.FieldDataCriteria;
import com.ge.predix.entity.fieldselection.FieldSelection;
import com.ge.predix.entity.getfielddata.GetFieldDataRequest;
import com.ge.predix.entity.getfielddata.GetFieldDataResult;
import com.ge.predix.entity.putfielddata.PutFieldDataCriteria;
import com.ge.predix.entity.putfielddata.PutFieldDataRequest;
import com.ge.predix.entity.putfielddata.PutFieldDataResult;
import com.ge.predix.solsvc.ext.util.JsonMapper;
import com.ge.predix.solsvc.fdh.client.config.IFdhRestConfig;
import com.ge.predix.solsvc.restclient.config.IOauthRestConfig;
import com.ge.predix.solsvc.restclient.impl.RestClient;

/**
 * 
 * @author predix -
 */
@Component
public class RefAppAnalytic implements IRefAppAnalytic {

	private static final Logger log = LoggerFactory
			.getLogger(RefAppAnalytic.class.getName());

	@Autowired
	@Qualifier("restClient")
	private RestClient restClient;

	@Autowired
	@Qualifier("defaultOauthRestConfig")
	private IOauthRestConfig restConfig;

	private static final String PORT_NAME_ALARM_HIHI = "ALARM_HIHI"; //$NON-NLS-1$
	private static final String PORT_NAME_ALARM_LOLO = "ALARM_LOLO"; //$NON-NLS-1$
	private static final String PORT_NAME_ALARM_HI = "ALARM_HI"; //$NON-NLS-1$
	private static final String PORT_NAME_ALARM_LO = "ALARM_LO"; //$NON-NLS-1$
	private static final String PORT_NAME_TS_DATA = "TS_DATA"; //$NON-NLS-1$
	// Outputs
	private static final String ALARM_LEVEL = "ALARM_LEVEL"; //$NON-NLS-1$
	private static final String ALARM_STATUS = "ALARM_STATUS"; //$NON-NLS-1$
	private static final String ALARM_THRESHOLDDIFF = "ALARM_THRESHOLDDIFF"; //$NON-NLS-1$
	private static final String ALARM_THRESHOLDLEVEL = "ALARM_THRESHOLDLEVEL"; //$NON-NLS-1$
	private static final String ALARM_LEVEL_VALUE = "ALARM_LEVEL_VALUE"; //$NON-NLS-1$
	private static final String ALARM_LEVEL_VALUE_TIME = "ALARM_LEVEL_VALUE_TIME"; //$NON-NLS-1$

	@Autowired
	private JsonMapper jsonMapper;

	@Autowired
	private IFdhRestConfig fdhRestConfig;

	/**
	 * @param request
	 *            -
	 * @return -
	 */
	@SuppressWarnings("nls")
	@Override
	public RunAnalyticResult runAnalytic(RunAnalyticRequest request) {
		List<Header> headers = this.restClient.getSecureTokenForClientId();
		headers.add(new BasicHeader("Content-Type", "application/json"));
		headers.add(new BasicHeader("Accept", "application/json"));

		// Resolve the Data - the RunAnalyticRequest contains Filters
		// for various situations (pullData(cloud,db), nearData(hadoop,etc),
		// distributedData, localData)
		Map<String, FieldData> inputMap = resolveData(request, headers);

		// calculate
		Map<String, FieldData> calculatedOutputMap = callAnalytic(request,
				inputMap);

		// FDH PutFieldData - the RunAnalyticRequest contains Filters
		// for the outputs. potential to cache data using a FDH CacheHandler
		RunAnalyticResult response = storeData(request, headers,
				calculatedOutputMap);

		return response;
	}

	/**
	 * @param request
	 * @param headers
	 * @param calculatedOutputMap
	 * @return -
	 */
	@SuppressWarnings("nls")
	private RunAnalyticResult storeData(RunAnalyticRequest request,
			List<Header> headers, Map<String, FieldData> calculatedOutputMap) {
		log.info("Put Field Data ===>Request URL........................."
				+ this.fdhRestConfig.getPutFieldDataEndPoint());
		String putRequest = this.jsonMapper
				.toJson(constructPutFieldDataRequest(request,
						calculatedOutputMap));
		log.info("Put Field Data ===>Request........................."
				+ putRequest);

		CloseableHttpResponse putResponse = this.restClient.post(
				this.fdhRestConfig.getPutFieldDataEndPoint(), putRequest,
				headers, this.restConfig.getDefaultConnectionTimeout(), this.restConfig.getDefaultSocketTimeout());
		String putResultString = this.restClient.getResponse(putResponse);
		log.info("Put field response = " + putResultString);
		PutFieldDataResult putResult = this.jsonMapper.fromJson(
				putResultString, PutFieldDataResult.class);

		// format Analytic Response
		RunAnalyticResult response = new RunAnalyticResult();
		response.getErrorEvent().addAll(putResult.getErrorEvent());

		for (Map.Entry<String, FieldData> entry : calculatedOutputMap
				.entrySet()) {
			PortIdentifier outputPortId = new PortIdentifier();
			outputPortId.setId(entry.getKey());
			outputPortId.setName(entry.getKey());

			Port outputPort = new Port();
			outputPort.setPortIdentifier(outputPortId);
			outputPort.setData(entry.getValue().getData());

			response.getOutputPort().add(outputPort);
		}
		return response;
	}

	/**
	 * @param request
	 * @param headers
	 * @return -
	 */
	@SuppressWarnings("nls")
	private Map<String, FieldData> resolveData(RunAnalyticRequest request,
			List<Header> headers) {
		// prepare request
		GetFieldDataRequest getFieldDataRequest = getFieldDataRequest(request);
		String getRequest = this.jsonMapper.toJson(getFieldDataRequest);
		log.info("Get Field Data ===>Request........................."
				+ getRequest);

		// call FDH.GetFieldData
		log.info("Get Field Data ===>Request URL........................."
				+ this.fdhRestConfig.getGetFieldDataEndPoint());
		CloseableHttpResponse getFieldDataResponse = this.restClient.post(
				this.fdhRestConfig.getGetFieldDataEndPoint(), getRequest,
				headers, this.restConfig.getDefaultConnectionTimeout(), this.restConfig.getDefaultSocketTimeout());
		log.info("Get Field Data ===> Response received........................."
				+ getFieldDataResponse);
		String getResultString = this.restClient
				.getResponse(getFieldDataResponse);
		log.info("Get field response = " + getResultString);
		GetFieldDataResult getResult = this.jsonMapper.fromJson(
				getResultString, GetFieldDataResult.class);

		// create a map to send to Calculation
		Map<String, FieldData> inputMap = new HashMap<String, FieldData>();
		for (FieldData fieldData : getResult.getFieldData()) {
			inputMap.put(fieldData.getResultId(), fieldData);
		}
		return inputMap;
	}

	private Map<String, FieldData> callAnalytic(
			RunAnalyticRequest serviceRequest, Map<String, FieldData> inputMap) {
		// Output from calculation
		SDDataEvent calculationResult = new SDDataEvent();
		DMBool alarmStatusDataEvent = new DMBool();
		DAString alarmLevelDataEvent = new DAString();
		DMReal alarmLevelValueDataEvent = new DMReal();
		DMReal alarmLevelValueTimeDataEvent = new DMReal();
		DMReal thresholdDiffDataEvent = new DMReal();
		DAString thresholdLevelDataEvent = new DAString();

		DataEvent tsData = inputMap.get(PORT_NAME_TS_DATA) != null ? ((OsaData) inputMap
				.get(PORT_NAME_TS_DATA).getData()).getDataEvent() : null;
		DataEvent alarmHiHiData = inputMap.get(PORT_NAME_ALARM_HIHI) != null ? ((OsaData) inputMap
				.get(PORT_NAME_ALARM_HIHI).getData()).getDataEvent() : null;
		DataEvent alarmLoLoData = inputMap.get(PORT_NAME_ALARM_LOLO) != null ? ((OsaData) inputMap
				.get(PORT_NAME_ALARM_LOLO).getData()).getDataEvent() : null;
		DataEvent alarmHiData = inputMap.get(PORT_NAME_ALARM_HI) != null ? ((OsaData) inputMap
				.get(PORT_NAME_ALARM_HI).getData()).getDataEvent() : null;
		DataEvent alarmLoData = inputMap.get(PORT_NAME_ALARM_LO) != null ? ((OsaData) inputMap
				.get(PORT_NAME_ALARM_LO).getData()).getDataEvent() : null;

		calculateAlarmData(calculationResult, alarmStatusDataEvent,
				alarmLevelDataEvent, thresholdDiffDataEvent,
				thresholdLevelDataEvent, alarmLevelValueDataEvent,
				alarmLevelValueTimeDataEvent, tsData != null ? tsData : null,
				alarmHiHiData != null ? alarmHiHiData : null,
				alarmLoLoData != null ? alarmLoLoData : null,
				alarmHiData != null ? alarmHiData : null,
				alarmLoData != null ? alarmLoData : null);

		Map<String, FieldData> outputMap = new HashMap<String, FieldData>();
		for (Port aPort : serviceRequest.getOutputPort()) {
			String aPortId = (String) aPort.getPortIdentifier().getId();
			if (aPortId.equalsIgnoreCase(ALARM_STATUS)) {
				outputMap.put(
						ALARM_STATUS,
						createFieldData(aPort.getFieldSelection(),
								alarmStatusDataEvent));
			} else if (aPortId.equalsIgnoreCase(ALARM_LEVEL)) {
				outputMap.put(
						ALARM_LEVEL,
						createFieldData(aPort.getFieldSelection(),
								alarmLevelDataEvent));
			} else if (aPortId.equalsIgnoreCase(ALARM_LEVEL_VALUE)) {
				outputMap.put(
						ALARM_LEVEL_VALUE,
						createFieldData(aPort.getFieldSelection(),
								alarmLevelValueDataEvent));
			} else if (aPortId.equalsIgnoreCase(ALARM_LEVEL_VALUE_TIME)) {
				outputMap.put(
						ALARM_LEVEL_VALUE_TIME,
						createFieldData(aPort.getFieldSelection(),
								alarmLevelValueTimeDataEvent));
			} else if (aPortId.equalsIgnoreCase(ALARM_THRESHOLDDIFF)) {
				outputMap.put(
						ALARM_THRESHOLDDIFF,
						createFieldData(aPort.getFieldSelection(),
								thresholdDiffDataEvent));
			} else if (aPortId.equalsIgnoreCase(ALARM_THRESHOLDLEVEL)) {
				outputMap.put(
						ALARM_THRESHOLDLEVEL,
						createFieldData(aPort.getFieldSelection(),
								thresholdLevelDataEvent));
			}
		}

		return outputMap;
	}

	private FieldData createFieldData(FieldSelection fieldSelection,
			DataEvent de) {

		Field field = new Field();
		field.setFieldIdentifier(fieldSelection.getFieldIdentifier());

		OsaData osaData = new OsaData();
		osaData.setDataEvent(de);

		FieldData fieldData = new FieldData();
		fieldData.getField().add(field);
		fieldData.setData(osaData);

		return fieldData;
	}

	/**
	 * @param tagAlarmStatusList
	 *            -
	 * @return -
	 */
	protected double getAverage(List<Boolean> tagAlarmStatusList) {
		int countTagsWithNoAlarms = 0;
		for (Boolean tagAlarmStatus : tagAlarmStatusList) {
			if (!tagAlarmStatus) {
				countTagsWithNoAlarms++;
			}
		}
		return ((double) countTagsWithNoAlarms / tagAlarmStatusList.size()) * 100;
	}

	private GetFieldDataRequest getFieldDataRequest(
			RunAnalyticRequest serviceRequest) {
		GetFieldDataRequest getFieldDataRequest = new GetFieldDataRequest();

		// Input Ports
		List<Port> inputPort = serviceRequest.getInputPort();

		for (Port aPort : inputPort) {
			FieldDataCriteria fieldDataCriteria = new FieldDataCriteria();
			fieldDataCriteria.setResultId(aPort.getPortIdentifier().getId()
					.toString());
			// SELECT
			fieldDataCriteria.getFieldSelection()
					.add(aPort.getFieldSelection());
			// WHERE
			fieldDataCriteria.setFilter(aPort.getFilter());

			getFieldDataRequest.getFieldDataCriteria().add(fieldDataCriteria);
		}

		return getFieldDataRequest;
	}

	private PutFieldDataRequest constructPutFieldDataRequest(
			RunAnalyticRequest serviceRequest, Map<String, FieldData> outputMap) {
		PutFieldDataRequest putFieldDataRequest = new PutFieldDataRequest();


		for (Port port : serviceRequest.getOutputPort()) {
			PutFieldDataCriteria putFieldDataCriteria = new PutFieldDataCriteria();
			FieldData data = outputMap.get(port.getPortIdentifier().getId());
			putFieldDataCriteria.setFieldData(data);
			putFieldDataCriteria.setFilter(port.getFilter());
			putFieldDataRequest.getPutFieldDataCriteria().add(
					putFieldDataCriteria);
		}

		return putFieldDataRequest;
	}

	@SuppressWarnings("nls")
	private void calculateAlarmData(SDDataEvent sdDataEvent,
			DataEvent alarmStatusDataEvent, DataEvent alarmLevelDataEvent,
			DataEvent thresholdDiffDataEvent,
			DataEvent thresholdLevelDataEvent,
			DataEvent alarmLevelValueDataEvent,
			DataEvent alarmLevelValueTimeDataEvent, DataEvent actualValueSeq,
			DataEvent alarmHIHISeq, DataEvent alarmLOLOSeq,
			DataEvent alarmHISeq, DataEvent alarmLOSeq) {

		Double hihiValue = Double.NaN;
		Double loloValue = Double.NaN;
		Double hiValue = Double.NaN;
		Double loValue = Double.NaN;

		if (alarmHISeq instanceof DMReal) {
			log.debug("invoked with DAString");
			alarmHIHISeq = alarmHIHISeq != null ? adaptToSDTestFromDMReal((DMReal) alarmHIHISeq)
					: null;
			alarmHISeq = alarmHISeq != null ? adaptToSDTestFromDMReal((DMReal) alarmHISeq)
					: null;
			alarmLOLOSeq = alarmLOLOSeq != null ? adaptToSDTestFromDMReal((DMReal) alarmLOLOSeq)
					: null;
			alarmLOSeq = alarmLOSeq != null ? adaptToSDTestFromDMReal((DMReal) alarmLOSeq)
					: null;
		}

		if (actualValueSeq instanceof DAString) {
			DMDataSeq actualValueSeqNew = new DMDataSeq();
			actualValueSeqNew.setValues(new ArrayList<Double>());
			actualValueSeqNew.setXAxisDeltas(new ArrayList<Double>());
			DAString actualValueStr = (DAString) actualValueSeq;
			String[] vals = null;
			if (actualValueStr.getValue() != null)
				vals = actualValueStr.getValue().split(",");
			double i = 0;
			if (vals != null)
				for (String s : vals) {
					actualValueSeqNew.getXAxisDeltas().add(i++);
					actualValueSeqNew.getValues().add(Double.valueOf(s));
				}
			actualValueSeq = actualValueSeqNew;
		}

		if (alarmHIHISeq != null
				&& !Double.isNaN(((SDTestReal) alarmHIHISeq).getMeasure())) {
			hihiValue = new Double(((SDTestReal) alarmHIHISeq).getMeasure());
		}
		if (alarmLOLOSeq != null
				&& !Double.isNaN(((SDTestReal) alarmLOLOSeq).getMeasure())) {
			loloValue = new Double(((SDTestReal) alarmLOLOSeq).getMeasure());
		}
		if (alarmHISeq != null
				&& !Double.isNaN(((SDTestReal) alarmHISeq).getMeasure())) {
			hiValue = new Double(((SDTestReal) alarmHISeq).getMeasure());
		}
		if (alarmLOSeq != null
				&& !Double.isNaN(((SDTestReal) alarmLOSeq).getMeasure())) {
			loValue = new Double(((SDTestReal) alarmLOSeq).getMeasure());
		}

		/*
		 * Validate HighHigh, LowLow, and ActualLevel level values. Test Actual
		 * Level as follows and assign resulting Alarm: Actual Level >=HighHigh.
		 * Alarm is HighHigh if High is not null and Actual Level >=High, Alarm
		 * is High if High is null, Alarm is NONE If Low is not null and Actual
		 * Level <= Actual Level, Alarm is Low If Low is null, Alarm is NONE
		 * Actual Level <= LowLow. Alarm is LowLow.
		 */

		if (actualValueSeq != null) {
			DMDataSeq actualValueDMDataSeq = (DMDataSeq) actualValueSeq;

			GregorianCalendar gregorianCalendarForXstart = (GregorianCalendar) GregorianCalendar
					.getInstance();
			if (actualValueDMDataSeq.getXAxisStart() == null)
				gregorianCalendarForXstart.setTimeInMillis(0); // set to
																// beginning
																// of time
			else
				gregorianCalendarForXstart.setTimeInMillis(actualValueDMDataSeq
						.getXAxisStart().longValue());

			long actualValueOccuredTimeWithxaxisDeltaTime = gregorianCalendarForXstart
					.getTimeInMillis();

			Double thresholdDivisor = null;
			if (!hihiValue.isNaN() && loloValue.isNaN())
				thresholdDivisor = hihiValue.doubleValue()
						- loloValue.doubleValue();
			else
				thresholdDivisor = hiValue.doubleValue()
						- loValue.doubleValue();

			for (int i = 0; actualValueDMDataSeq.getValues() != null
					&& i < actualValueDMDataSeq.getValues().size()
					&& i < actualValueDMDataSeq.getXAxisDeltas().size(); i++) {
				String alarmMessage = "ALARM_NORMAL";

				double actualValue = actualValueDMDataSeq.getValues().get(i);
				double actualValueOccuredTime = actualValueDMDataSeq
						.getXAxisDeltas().get(i);

				actualValueOccuredTimeWithxaxisDeltaTime = actualValueOccuredTimeWithxaxisDeltaTime
						+ (long) actualValueOccuredTime;

				log.info("ACTUAL VALUE =====" + actualValue);
				log.info("Max value =====" + hiValue.doubleValue());
				log.info("Min value =====" + loValue.doubleValue());
				
				if (!hihiValue.isNaN()
						&& actualValue >= hihiValue.doubleValue()) {
					alarmMessage = "ALARM_HIHI";
					((DMBool) alarmStatusDataEvent).setValue(true);
					((DAString) alarmLevelDataEvent).setValue(alarmMessage);
					((DMReal) thresholdDiffDataEvent).setValue((hihiValue
							.doubleValue() - actualValue) / thresholdDivisor);
					((DAString) thresholdLevelDataEvent).setValue(alarmMessage);
					((DMReal) alarmLevelValueDataEvent).setValue(actualValue);
					((DMReal) alarmLevelValueTimeDataEvent)
							.setValue(actualValueOccuredTimeWithxaxisDeltaTime);
				} else if (!hiValue.isNaN()
						&& actualValue >= hiValue.doubleValue()) {
					alarmMessage = "ALARM_HI";
					log.info("ALARM ALARM ALARM...");
					((DMBool) alarmStatusDataEvent).setValue(true);
					((DAString) alarmLevelDataEvent).setValue(alarmMessage);
					((DMReal) thresholdDiffDataEvent).setValue((hiValue
							.doubleValue() - actualValue) / thresholdDivisor);
					((DAString) thresholdLevelDataEvent).setValue(alarmMessage);
					((DMReal) alarmLevelValueDataEvent).setValue(actualValue);
					((DMReal) alarmLevelValueTimeDataEvent)
							.setValue(actualValueOccuredTimeWithxaxisDeltaTime);
				} else if (!loValue.isNaN()
						&& actualValue <= loValue.doubleValue()) {
					alarmMessage = "ALARM_LO";
					log.info("ALARM ALARM ALARM...");
					((DMBool) alarmStatusDataEvent).setValue(true);
					((DAString) alarmLevelDataEvent).setValue(alarmMessage);
					((DMReal) thresholdDiffDataEvent).setValue((loValue
							.doubleValue() - actualValue) / thresholdDivisor);
					((DAString) thresholdLevelDataEvent).setValue(alarmMessage);
					((DMReal) alarmLevelValueDataEvent).setValue(actualValue);
					((DMReal) alarmLevelValueTimeDataEvent)
							.setValue(actualValueOccuredTimeWithxaxisDeltaTime);
				} else if (!loloValue.isNaN()
						&& actualValue <= loloValue.doubleValue()) {
					alarmMessage = "ALARM_LOLO";
					((DMBool) alarmStatusDataEvent).setValue(true);
					((DAString) alarmLevelDataEvent).setValue(alarmMessage);
					((DMReal) thresholdDiffDataEvent).setValue((loloValue
							.doubleValue() - actualValue) / thresholdDivisor);
					((DAString) thresholdLevelDataEvent).setValue(alarmMessage);
					((DMReal) alarmLevelValueDataEvent).setValue(actualValue);
					((DMReal) alarmLevelValueTimeDataEvent)
							.setValue(actualValueOccuredTimeWithxaxisDeltaTime);
				} else {
					alarmMessage = "NORMAL";
					((DMBool) alarmStatusDataEvent).setValue(false);
					((DAString) alarmLevelDataEvent).setValue(alarmMessage);
					if (!loValue.isNaN() && !hiValue.isNaN()) {
						if (hiValue.doubleValue() - actualValue < actualValue
								- loValue.doubleValue()) {
							((DMReal) thresholdDiffDataEvent).setValue((hiValue
									.doubleValue() - actualValue)
									/ thresholdDivisor);
							((DAString) thresholdLevelDataEvent)
									.setValue("ALARM_HI");
						} else {
							((DAString) thresholdLevelDataEvent)
									.setValue("ALARM_LO");
							((DMReal) thresholdDiffDataEvent)
									.setValue((actualValue - loValue
											.doubleValue()) / thresholdDivisor);
						}
					} else if (!loloValue.isNaN() && !hihiValue.isNaN()) {
						if (hihiValue.doubleValue() - actualValue < actualValue
								- loloValue.doubleValue())
							((DMReal) thresholdDiffDataEvent).setValue((hiValue
									.doubleValue() - actualValue)
									/ thresholdDivisor);
						else
							((DMReal) thresholdDiffDataEvent)
									.setValue((actualValue - loloValue
											.doubleValue()) / thresholdDivisor);
					}
					((DMReal) alarmLevelValueDataEvent).setValue(actualValue);
					((DMReal) alarmLevelValueTimeDataEvent)
							.setValue(actualValueOccuredTimeWithxaxisDeltaTime);
				}

				// Double midRange = (thresholdMax - thresholdMin)/2;
				// if(currentValue.compareTo(midRange) >= 0){
				// Double value= ((thresholdMax - currentValue )/(thresholdMax -
				// thresholdMin) ) * 100;

				NumAlert numAlert = new NumAlert();
				OsacbmTime osacbmTime = new OsacbmTime();
				BigInteger bigInteger = BigInteger
						.valueOf((long) actualValueOccuredTimeWithxaxisDeltaTime);
				osacbmTime.setTimeBinary(bigInteger);
				numAlert.setLastTrigger(osacbmTime);
				numAlert.setAlertName(alarmMessage);
				((SDDataEvent) sdDataEvent).getNumAlerts().add(numAlert);
			}
		}
	}

	private SDTestReal adaptToSDTestFromDMReal(DMReal value) {
		if (value == null)
			return null;
		SDTestReal valueNew = new SDTestReal();
		valueNew.setMeasure(Double.valueOf(value.getValue()));
		return valueNew;
	}
}
