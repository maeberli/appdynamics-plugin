package nl.codecentric.jenkins.appd;

import hudson.model.*;
import nl.codecentric.jenkins.appd.rest.types.ApplicationInformation;
import nl.codecentric.jenkins.appd.rest.types.MetricData;
import nl.codecentric.jenkins.appd.rest.RestConnection;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

/**
 * The {@link AppDynamicsDataCollector} will eventually fetch the performance
 * statistics from the AppDynamics REST interface and parse them into a
 * {@link AppDynamicsReport}.<br />
 * <br />
 * Perhaps create separate Collectors again when this is more logical to create
 * separate graphs. For now this single collector should get all data.
 */
public class AppDynamicsDataCollector {
	public static final String CUSTOM_METRIC_PATH = "Custom metric path";
	private static final Logger LOG = Logger.getLogger(AppDynamicsDataCollector.class.getName());
	private static final String[] METRIC_PATHS = { "Overall Application Performance|Average Response Time (ms)",
			"Overall Application Performance|Calls per Minute",
			"Overall Application Performance|Normal Average Response Time (ms)",
			"Overall Application Performance|Number of Slow Calls",
			"Overall Application Performance|Number of Very Slow Calls",
			"Overall Application Performance|Errors per Minute",
			"Overall Application Performance|Exceptions per Minute",
			"Overall Application Performance|Infrastructure Errors per Minute", CUSTOM_METRIC_PATH };

	private final RestConnection restConnection;
	private final AbstractBuild<?, ?> build;
	private final int minimumDurationInMinutes;
	private final String customMetricPath;

	public AppDynamicsDataCollector(final RestConnection connection, final AbstractBuild<?, ?> build,
			final String customMetricPath, final int minimumDurationInMinutes) {
		this.restConnection = connection;
		this.build = build;
		this.customMetricPath = customMetricPath;
		this.minimumDurationInMinutes = minimumDurationInMinutes;
	}

	public static final String[] getAvailableMetricPaths() {
		return Arrays.copyOf(METRIC_PATHS, METRIC_PATHS.length);
	}

	public static String[] getMergedMetricPaths(String customMetricPath) {
		List<String> result = new ArrayList<String>();
		for (String urlStr : Arrays.asList(METRIC_PATHS)) {
			try {
				if (urlStr.equals(CUSTOM_METRIC_PATH)) {
					urlStr = customMetricPath;
				}
				String encodedUrlStr = URLEncoder.encode(urlStr, "UTF8");
				result.add(encodedUrlStr);
			} catch (Exception e) {
			}
		}
		return result.toArray(new String[0]);
	}

	/** Parses the specified reports into {@link AppDynamicsReport}s. */
	public AppDynamicsReport createReportFromMeasurements() {
		long buildStartTime = build.getRootBuild().getTimeInMillis();
		int durationInMinutes = calculateDurationToFetch(buildStartTime);

		LOG.fine(String.format("Current time: %d - Build time: %d - Duration: %d", System.currentTimeMillis(),
				buildStartTime, durationInMinutes));

		AppDynamicsReport adReport = new AppDynamicsReport(buildStartTime, durationInMinutes);
		for (String metricPath : METRIC_PATHS) {
			if (metricPath.equals(CUSTOM_METRIC_PATH))
				metricPath = customMetricPath;

			final MetricData metric = restConnection.fetchMetricData(metricPath, durationInMinutes, buildStartTime);
			if (adReport != null && metric != null) {
				adReport.addMetrics(metric);
			}
		}

		ApplicationInformation applicationInformation = this.restConnection.fetchApplicationData();
		if (applicationInformation != null) {
			adReport.setApplicationInformation(applicationInformation);
			adReport.setAppDynamicsControllerUri(restConnection.getControllerUri());
		}

		return adReport;
	}

	private int calculateDurationToFetch(final Long buildStartTime) {
		long duration = System.currentTimeMillis() - buildStartTime;

		int durationInMinutes = (int) (duration / (1000 * 60));
		if (durationInMinutes < minimumDurationInMinutes) {
			durationInMinutes = minimumDurationInMinutes;
		}

		return durationInMinutes;
	}

}
