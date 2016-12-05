package com.ge.predix.solsvc.refappanalytic.analytic;

import com.ge.predix.entity.analytic.runanalytic.RunAnalyticRequest;
import com.ge.predix.entity.analytic.runanalytic.RunAnalyticResult;



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
