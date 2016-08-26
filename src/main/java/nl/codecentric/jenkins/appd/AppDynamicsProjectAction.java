package nl.codecentric.jenkins.appd;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.CategoryDataset;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import hudson.model.*;
import hudson.util.ChartUtil;
import hudson.util.ChartUtil.NumberOnlyBuildLabel;
import hudson.util.DataSetBuilder;
import hudson.util.Graph;

/**
 * The {@link Action} that will be executed from your project and fetch the
 * AppDynamics performance data and display after a build. The Project Action
 * will show the graph for overall performance from all builds.
 */
public class AppDynamicsProjectAction implements Action {

	private static final String PROJECTACTION_DISPLAYNAME = "AppDynamics Project actions.";

	/**
	 * Logger.
	 */
	private static final long serialVersionUID = 1L;

	private static final String PLUGIN_NAME = "appdynamics-dashboard";

	private final String mainMetricKey;
	private String[] allMetricKeys;
	AbstractProject<?, ?> project;

	public AppDynamicsProjectAction(final AbstractProject<?, ?> project, final String mainMetricKey,
			final String[] allMetricKeys) {
		this.project = project;
		this.mainMetricKey = mainMetricKey;
		this.allMetricKeys = Arrays.copyOf(allMetricKeys, allMetricKeys.length);
	}

	public String getDisplayName() {
		return PROJECTACTION_DISPLAYNAME.toString();
	}

	public String getUrlName() {
		return PLUGIN_NAME;
	}

	public String getIconFileName() {
		return "graph.gif";
	}

	/**
	 * Method necessary to get the side-panel included in the Jelly file
	 * 
	 * @return this {@link AbstractProject}
	 */
	public AbstractProject<?, ?> getProject() {
		return this.project;
	}

	public boolean isTrendVisibleOnProjectDashboard() {
		return getExistingReportsList().size() >= 1;
	}

	public List<String> getAvailableMetricKeys() {
		return Arrays.asList(allMetricKeys);
	}

	/**
	 * Graph of metric points over time.
	 */
	public void doSummarizerGraphMainMetric(final StaplerRequest request, final StaplerResponse response)
			throws IOException {
		final Map<ChartUtil.NumberOnlyBuildLabel, Double> averagesFromReports = getAveragesFromAllReports(
				getExistingReportsList(), mainMetricKey);

		final Graph graph = new GraphImpl(mainMetricKey + " Overall Graph") {

			protected DataSetBuilder<String, ChartUtil.NumberOnlyBuildLabel> createDataSet() {
				DataSetBuilder<String, ChartUtil.NumberOnlyBuildLabel> dataSetBuilder = new DataSetBuilder<String, ChartUtil.NumberOnlyBuildLabel>();

				Iterator<Entry<NumberOnlyBuildLabel, Double>> it = averagesFromReports.entrySet().iterator();
				while (it.hasNext()) {
					Entry<NumberOnlyBuildLabel, Double> entry = it.next();
					ChartUtil.NumberOnlyBuildLabel label = (ChartUtil.NumberOnlyBuildLabel) entry.getKey();
					Double value = (Double) entry.getValue();
					dataSetBuilder.add(value, mainMetricKey, label);
				}

				return dataSetBuilder;
			}
		};

		graph.doPng(request, response);
	}

	/**
	 * Graph of metric points over time, metric to plot set as request
	 * parameter.
	 */
	public void doSummarizerGraphForMetric(final StaplerRequest request, final StaplerResponse response)
			throws IOException {
		final String metricKey = request.getParameter("metricDataKey");
		final Map<ChartUtil.NumberOnlyBuildLabel, Double> averagesFromReports = getAveragesFromAllReports(
				getExistingReportsList(), metricKey);

		final Graph graph = new GraphImpl(metricKey + " Overall Graph") {

			protected DataSetBuilder<String, ChartUtil.NumberOnlyBuildLabel> createDataSet() {
				DataSetBuilder<String, ChartUtil.NumberOnlyBuildLabel> dataSetBuilder = new DataSetBuilder<String, ChartUtil.NumberOnlyBuildLabel>();

				Iterator<Entry<NumberOnlyBuildLabel, Double>> it = averagesFromReports.entrySet().iterator();
				while (it.hasNext()) {
					Entry<NumberOnlyBuildLabel, Double> entry = it.next();
					ChartUtil.NumberOnlyBuildLabel label = (ChartUtil.NumberOnlyBuildLabel) entry.getKey();
					Double value = (Double) entry.getValue();
					dataSetBuilder.add(value, metricKey, label);
				}

				return dataSetBuilder;
			}
		};

		graph.doPng(request, response);
	}

	private abstract class GraphImpl extends Graph {
		private final String graphTitle;

		protected GraphImpl(final String metricKey) {
			super(-1, 400, 300); // cannot use timestamp, since ranges may
									// change
			this.graphTitle = stripTitle(metricKey);
		}

		private String stripTitle(final String metricKey) {
			return metricKey.substring(metricKey.lastIndexOf("|") + 1);
		}

		protected abstract DataSetBuilder<String, ChartUtil.NumberOnlyBuildLabel> createDataSet();

		protected JFreeChart createGraph() {
			final CategoryDataset dataset = createDataSet().build();

			final JFreeChart chart = ChartFactory.createLineChart(graphTitle, // title
					"Build Number #", // category axis label
					null, // value axis label
					dataset, // data
					PlotOrientation.VERTICAL, // orientation
					false, // include legend
					true, // tooltips
					false // urls
			);

			chart.setBackgroundPaint(Color.white);

			return chart;
		}
	}

	private List<AppDynamicsReport> getExistingReportsList() {
		final List<AppDynamicsReport> adReportList = new ArrayList<AppDynamicsReport>();

		if (null == this.project) {
			return adReportList;
		}

		final List<? extends AbstractBuild<?, ?>> builds = project.getBuilds();
		for (AbstractBuild<?, ?> currentBuild : builds) {
			final AppDynamicsBuildAction performanceBuildAction = currentBuild.getAction(AppDynamicsBuildAction.class);
			if (performanceBuildAction == null) {
				continue;
			}
			final AppDynamicsReport report = performanceBuildAction.getBuildActionResultsDisplay()
					.getAppDynamicsReport();
			if (report == null) {
				continue;
			}

			adReportList.add(report);
		}

		return adReportList;
	}

	private Map<ChartUtil.NumberOnlyBuildLabel, Double> getAveragesFromAllReports(final List<AppDynamicsReport> reports,
			final String metricKey) {
		Map<ChartUtil.NumberOnlyBuildLabel, Double> averages = new TreeMap<ChartUtil.NumberOnlyBuildLabel, Double>();
		for (AppDynamicsReport report : reports) {
			double value = report.getAverageForMetric(metricKey);
			if (value >= 0) {
				ChartUtil.NumberOnlyBuildLabel label = new ChartUtil.NumberOnlyBuildLabel(
						(Run<?, ?>) report.getBuild());
				averages.put(label, value);
			}
		}

		return averages;
	}
}
