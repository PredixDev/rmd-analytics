package com.ge.predix.solsvc.refappanalytic.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.ge.predix.entity.analytic.runanalytic.RunAnalyticRequest;
import com.ge.predix.entity.analytic.runanalytic.RunAnalyticResult;
import com.ge.predix.solsvc.ext.util.JsonMapper;
import com.ge.predix.solsvc.refappanalytic.analytic.IRefAppAnalytic;

/**
 * 
 * @author 212367843 Sankar -
 */
@RestController
@RequestMapping(value = "api/v1/ref_app_analytic")
public class RefAppAnalyticController
{

    private static final Logger log = LoggerFactory.getLogger(RefAppAnalyticController.class.getName());

    @Autowired
    private IRefAppAnalytic      analyticSvc;

    @Autowired
    private JsonMapper          jsonMapper;

    /**
     * @param input
     *            -
     * @return -
     */
    @SuppressWarnings("nls")
    @RequestMapping(value = "/execution", method = RequestMethod.POST)
    public ResponseEntity<String> execute(@RequestBody String input)
    {

        log.info("In Ref App Analytics.." + input);
        try
        {
            // Extract
            RunAnalyticRequest runAnalyticRequest = this.jsonMapper.fromJson(input, RunAnalyticRequest.class);
            log.info("Request received = " + runAnalyticRequest);

            // Transform and Load
            RunAnalyticResult runAnalyticResponse = this.analyticSvc.runAnalytic(runAnalyticRequest);

            // Return
            String runAnalyticResponseStr = this.jsonMapper.toJson(runAnalyticResponse);
            log.info("Response = " + runAnalyticResponseStr);
            return new ResponseEntity<String>(runAnalyticResponseStr, HttpStatus.OK);
        }
        catch (Throwable ex)
        {
            log.error("Error in Ref app analytics =" + input, ex);
            throw new RuntimeException(ex);
        }
    }

}
