package nl.codecentric.jenkins.appd.rest;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;

import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.json.JSONConfiguration;
import com.sun.jersey.client.apache.ApacheHttpClient;
import com.sun.jersey.client.apache.config.DefaultApacheHttpClientConfig;
import com.sun.jersey.core.util.MultivaluedMapImpl;

import nl.codecentric.jenkins.appd.rest.types.ApplicationInformation;
import nl.codecentric.jenkins.appd.rest.types.MetricData;

/**
 * Class providing only the connection to the AppDynamics REST interface. Checks
 * all connection parameters and maintains the connection to the REST interface.
 */
public class RestConnection {

	private static final String REST_SEGMENT_METRIC_DATA = "metric-data";
	private static final String REST_PARAM_METRIC_PATH = "metric-path";
	private static final String REST_PARAM_TIME_RANGE_TYPE = "time-range-type";
	private static final String REST_PARAM_START_TIME = "start-time";
	private static final String REST_PARAM_DURATION_IN_MINS = "duration-in-mins";
	private static final String REST_PARAM_ROLLUP = "rollup";
	private static final String REST_PARAM_OUTPUT = "output";
	private static final String PARAM_TIME_RANGE_TYPE_AFTER_TIME = "AFTER_TIME";
	private static final String PARAM_TIME_RANGE_TYPE_BEFORE_NOW = "BEFORE_NOW";
	private static final String PARAM_DEFAULT_ROLLUP = "false";
	private static final String PARAM_DEFAULT_OUTPUT = "JSON";

	private static final Logger LOG = Logger.getLogger(RestConnection.class.getName());
	private final ObjectMapper jsonMapper = new ObjectMapper();
	private final WebResource restResource;
	private final String applicationName;
	private final String controllerUri;

	private static final String[] CONTROLLER_URI_ORDERED_SEGMENTS = { "controller" };
	private static final String[] REST_URI_ORDERED_SEGMENTS = { "controller", "rest", "applications" };

	public RestConnection(final String controllerUri, final String username, final String password,
			final String applicationName) {
		this.applicationName = applicationName;
		this.controllerUri = parseUri(controllerUri, CONTROLLER_URI_ORDERED_SEGMENTS);

		final String parsedUsername = parseUsername(username);
		final String parsedRestUri = parseUri(controllerUri, REST_URI_ORDERED_SEGMENTS);
		final String parsedApplicationName = parseApplicationName(this.applicationName);

		DefaultApacheHttpClientConfig config = new DefaultApacheHttpClientConfig();
		config.getState().setCredentials(null, null, -1, parsedUsername, password);
		config.getFeatures().put(JSONConfiguration.FEATURE_POJO_MAPPING, Boolean.TRUE);
		jsonMapper.configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		Client restClient = ApacheHttpClient.create(config);
		restClient.setFollowRedirects(true);

		restResource = restClient.resource(parsedRestUri + parsedApplicationName);
	}

	public boolean validateConnection() {
		boolean validationResult = false;

		try {
			ClientResponse response = restResource.path("business-transactions/").queryParam("output", "JSON")
					.accept(MediaType.APPLICATION_JSON_TYPE).get(ClientResponse.class);

			if (response.getStatus() == 200) {
				String output = response.getEntity(String.class);
				LOG.fine(String.format("Response from AppDynamics server ==> code: %s | output: %s",
						response.getStatus(), output));
				validationResult = true;
			}
		} catch (Exception e) {
			LOG.log(Level.INFO, "Some problem connecting to the AppDynamics REST interface, see stack-trace for "
					+ "more information", e);
		}

		return validationResult;
	}

	public MetricData fetchMetricData(final String metricPath, int durationInMinutes) {
		return fetchMetricData(metricPath, durationInMinutes, -1);
	}

	public MetricData fetchMetricData(final String metricPath, int durationInMinutes, long buildStartTime) {
		String encodedMetricPath = encodeRestSegment(metricPath);
		MultivaluedMap<String, String> paramMap = new MultivaluedMapImpl();
		paramMap.add(REST_PARAM_METRIC_PATH, encodedMetricPath);

		if (buildStartTime > 0) {
			paramMap.add(REST_PARAM_TIME_RANGE_TYPE, PARAM_TIME_RANGE_TYPE_AFTER_TIME);
			paramMap.add(REST_PARAM_START_TIME, Long.toString(buildStartTime));
		} else {
			paramMap.add(REST_PARAM_TIME_RANGE_TYPE, PARAM_TIME_RANGE_TYPE_BEFORE_NOW);
		}
		paramMap.add(REST_PARAM_DURATION_IN_MINS, Integer.toString(durationInMinutes));
		paramMap.add(REST_PARAM_ROLLUP, PARAM_DEFAULT_ROLLUP);
		paramMap.add(REST_PARAM_OUTPUT, PARAM_DEFAULT_OUTPUT);

		MetricData resultData = null;
		try {
			ClientResponse response = restResource.path(REST_SEGMENT_METRIC_DATA).queryParams(paramMap)
					.accept(MediaType.APPLICATION_JSON_TYPE).get(ClientResponse.class);

			if (response.getStatus() == 200) {
				String jsonOutput = response.getEntity(String.class);
				LOG.fine(String.format("Response from AppDynamics server ==> code: %s | output: %s",
						response.getStatus(), jsonOutput));

				List<MetricData> metricList = jsonMapper.readValue(jsonOutput, new TypeReference<List<MetricData>>() {
				});
				resultData = metricList.get(0); // Always expect only single
												// 'MetricData' value
				LOG.fine("Successfully fetched metrics for path: " + resultData.getMetricPath());
			}
		} catch (Exception e) {
			LOG.log(Level.INFO, "Some problem fetching metrics from the AppDynamics REST interface, "
					+ "see stack-trace for more information", e);
		}

		return resultData;
	}

	public ApplicationInformation fetchApplicationData() {

		ApplicationInformation applicationInformation = null;

		LOG.fine("fetch application and search for: " + applicationName);

		try {
			ClientResponse response = this.restResource.queryParam("output", "JSON")
					.accept(MediaType.APPLICATION_JSON_TYPE).get(ClientResponse.class);

			if (response.getStatus() == 200) {
				String jsonOutput = response.getEntity(String.class);
				LOG.fine(String.format("Response from AppDynamics server ==> code: %s | output: %s",
						response.getStatus(), jsonOutput));

				List<ApplicationInformation> applicationList = jsonMapper.readValue(jsonOutput,
						new TypeReference<List<ApplicationInformation>>() {
						});

				Iterator<ApplicationInformation> it = applicationList.iterator();

				while (it.hasNext() && applicationInformation == null) {
					ApplicationInformation info = it.next();
					if (info.getName().equals(this.applicationName)) {
						applicationInformation = info;
						LOG.fine("Successfully fetched application: " + applicationInformation);
					}
				}
			} else {
				LOG.warning(
						"Application fetch not successfull. Response: " + response.toString() + response.getLocation());
			}

		} catch (Exception e) {
			LOG.log(Level.INFO, "Some problem fetching Applications from the AppDynamics REST interface, "
					+ "see stack-trace for more information", e);
		}

		return applicationInformation;
	}

	public static boolean validateRestUri(final String restUri) {
		if (isFieldEmpty(restUri)) {
			return false;
		}

		if (restUri.startsWith("http://") || restUri.startsWith("https://")) {
			return true;
		} else {
			return false;
		}
	}

	public static boolean validateApplicationName(final String applicationName) {
		return !isFieldEmpty(applicationName);
	}

	public static boolean validateUsername(final String username) {
		return !isFieldEmpty(username);
	}

	public static boolean validatePassword(final String password) {
		return !isFieldEmpty(password);
	}

	public String getControllerUri() {
		return this.controllerUri;
	}

	private String parseUsername(final String username) {
		String parsedUsername = username;
		if (!username.contains("@")) {
			parsedUsername += "@customer1";
		}
		LOG.fine("Parsed username: " + parsedUsername);
		return parsedUsername;
	}

	private String parseUri(final String restUri, final String[] uriOrderedSegments) {
		StringBuilder parsedUri = new StringBuilder(parseRestSegment(restUri));

		for (String segment : uriOrderedSegments) {
			if (!restUri.contains(segment)) {
				parsedUri.append(segment + "/");
			}
		}
		LOG.fine("Parsed REST uri: " + parsedUri.toString());
		return parsedUri.toString();
	}

	private String parseApplicationName(final String applicationName) {
		String parsedApplicationName = encodeRestSegment(applicationName);
		LOG.fine("Parsed Application Name: " + parsedApplicationName);
		return parseRestSegment(parsedApplicationName);
	}

	private String encodeRestSegment(final String restSegment) {
		String encodedSegment;
		try {
			encodedSegment = URLEncoder.encode(restSegment, "UTF-8");
			// AppDynamics interface expects '%20' for spaces instead of '+'
			encodedSegment = encodedSegment.replaceAll("\\+", "%20");
		} catch (UnsupportedEncodingException e) {
			encodedSegment = restSegment;
		}

		return encodedSegment;
	}

	private String parseRestSegment(final String restSegment) {
		String parsedSegment = restSegment;
		if (!restSegment.endsWith("/")) {
			parsedSegment += "/";
		}

		return parsedSegment;
	}

	private static boolean isFieldEmpty(final String field) {
		if (field == null || field.isEmpty()) {
			return true;
		}

		final String trimmedField = field.trim();
		if (trimmedField.length() == 0) {
			return true;
		}

		return false;
	}

}
