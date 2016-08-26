package nl.codecentric.jenkins.appd;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import hudson.model.*;
import nl.codecentric.jenkins.appd.rest.types.ApplicationInformation;
import nl.codecentric.jenkins.appd.rest.types.MetricData;
import nl.codecentric.jenkins.appd.rest.types.MetricValues;

/**
 * Represents a single performance report
 */
public class AppDynamicsReport {

	private final Map<String, MetricData> keyedMetricDataMap = new LinkedHashMap<String, MetricData>();
	private final Long reportTimestamp;
	private final Integer reportDurationInMinutes;

	private AppDynamicsBuildAction buildAction;
	private AppDynamicsReport lastBuildReport;
	private ApplicationInformation applicationInformation;
	private String appDynamicsControllerUri;

	public AppDynamicsReport(final Long timestamp, final Integer durationInMinutes) {
		this.reportTimestamp = timestamp;
		this.reportDurationInMinutes = durationInMinutes;
	}

	public void addMetrics(final MetricData metrics) {
		keyedMetricDataMap.put(metrics.getMetricPath(), metrics);
	}

	public MetricData getMetricByKey(final String metricKey) {
		final MetricData selectedMetric = keyedMetricDataMap.get(metricKey);
		if (selectedMetric == null) {
			throw new IllegalArgumentException("Provided Metric Key is not available, tried to select; " + metricKey);
		}
		return selectedMetric;
	}

	public List<MetricData> getMetricsList() {
		return new ArrayList<MetricData>(keyedMetricDataMap.values());
	}

	public double getAverageForMetric(final String metricKey) {
		final MetricData selectedMetric = getMetricByKey(metricKey);

		long calculatedSum = 0;
		for (MetricValues value : selectedMetric.getMetricValues()) {
			calculatedSum += value.getValue();
		}

		final int numberOfMeasurements = selectedMetric.getMetricValues().size();
		double result = -1;
		if (numberOfMeasurements > 0) {
			result = (double) calculatedSum / (double) numberOfMeasurements;
		}

		return result;
	}

	public long getMaxForMetric(final String metricKey) {
		final MetricData selectedMetric = getMetricByKey(metricKey);

		long max = Long.MIN_VALUE;
		for (MetricValues value : selectedMetric.getMetricValues()) {
			max = Math.max(value.getMax(), max);
		}
		return max;
	}

	public long getMinForMetric(final String metricKey) {
		final MetricData selectedMetric = getMetricByKey(metricKey);

		long min = Long.MAX_VALUE;
		for (MetricValues value : selectedMetric.getMetricValues()) {
			min = Math.min(value.getMin(), min);
		}
		return min;
	}

	public String getName() {
		DateTimeFormatter dateTimeFormat = DateTimeFormat.mediumDateTime();
		return String.format("AppDynamics Metric Report for time %s - with a duration of %d minutes",
				dateTimeFormat.print(this.reportTimestamp), reportDurationInMinutes);
	}

	public String getDashboardUrl() {
		// http://10.66.12.9:8090/controller/#/location=APP_DASHBOARD&timeRange=Custom_Time_Range.BETWEEN_TIMES.1472195940000.1472194800000.19&application=17&dashboardMode=force"
		String url = "";
		if (applicationInformation != null) {
			url = buildDashboardURL(this.getEndTimestamp(), this.reportTimestamp, this.applicationInformation.getId());
		}
		return url;
	}

	public String getCompareReleaseUrl() {
		// http://10.66.12.9:8090/controller/#/location=APP_RELEASE_ANALYSIS&timeRange=Custom_Time_Range.BETWEEN_TIMES.1467676800000.1467673200000.60&application=17&timeRange1=Custom_Time_Range.BETWEEN_TIMES.1467687600000.1467684000000.60&timeRange2=Custom_Time_Range.BETWEEN_TIMES.1467676800000.1467673200000.60

		long currentEndTimestamp = this.getEndTimestamp();
		long currentTimestamp = this.getTimestamp();

		long lastBuildEndTimestamp = this.getEndTimestamp();
		long lastBuildTimestamp = this.getTimestamp();

		if (this.lastBuildReport != null) {
			lastBuildEndTimestamp = this.lastBuildReport.getEndTimestamp();
			lastBuildTimestamp = this.lastBuildReport.getTimestamp();
		}

		String url = "";
		if (applicationInformation != null) {
			url = buildCompareURL(currentEndTimestamp, currentTimestamp, lastBuildEndTimestamp, lastBuildTimestamp,
					applicationInformation.getId());
		}
		return url;
	}

	public long getTimestamp() {
		return reportTimestamp;
	}

	public long getEndTimestamp() {
		return getTimestamp() + (this.reportDurationInMinutes * (long) 60000);
	}

	public AbstractBuild<?, ?> getBuild() {
		return buildAction.getBuild();
	}

	public AppDynamicsBuildAction getBuildAction() {
		return buildAction;
	}

	void setBuildAction(AppDynamicsBuildAction buildAction) {
		this.buildAction = buildAction;
	}

	public void setLastBuildReport(AppDynamicsReport lastBuildReport) {
		this.lastBuildReport = lastBuildReport;
	}

	public void setApplicationInformation(ApplicationInformation applicationInformation) {
		this.applicationInformation = applicationInformation;
	}

	private String buildDashboardURL(final Long endTimestamp, final Long startTimestamp, final int applicationID) {
		return buildURL("APP_DASHBOARD", applicationID,
				String.format("&timeRange=Custom_Time_Range.BETWEEN_TIMES.%d.%d.19&dashboardMode=force", endTimestamp,
						startTimestamp));
	}

	private String buildCompareURL(final long currentEndTimestamp, final long currentTimestamp,
			final long lastBuildEndTimestamp, final long lastBuildTimestamp, final int applicationId) {
		return buildURL("APP_RELEASE_ANALYSIS", applicationId,
				String.format(
						"&timeRange=Custom_Time_Range.BETWEEN_TIMES.%d.%d.60&timeRange1=Custom_Time_Range.BETWEEN_TIMES.%d.%d.60&timeRange2=Custom_Time_Range.BETWEEN_TIMES.%d.%d.60",
						currentEndTimestamp, currentTimestamp, currentEndTimestamp, currentTimestamp,
						lastBuildEndTimestamp, lastBuildTimestamp));
	}

	private String buildURL(final String location, final int applicationID, final String additionalParameters) {
		return String.format("%s#/location=%s&application=%d%s", this.appDynamicsControllerUri, location, applicationID,
				additionalParameters);
	}

	public void setAppDynamicsControllerUri(String appDynamicsControllerUri) {
		this.appDynamicsControllerUri = appDynamicsControllerUri;
	}

}
