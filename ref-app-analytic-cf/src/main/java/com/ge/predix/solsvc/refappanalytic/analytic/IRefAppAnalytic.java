package com.ge.predix.solsvc.refappanalytic.analytic;

import com.ge.dsp.pm.analytic.entity.runanalytic.RunAnalyticRequest;
import com.ge.dsp.pm.analytic.entity.runanalytic.RunAnalyticResult;



/**
 * 
 * @author predix -
 */
public interface IRefAppAnalytic
{   
    /**
     * @param request -
     * @return -
     */
    RunAnalyticResult runAnalytic(RunAnalyticRequest request);
}
