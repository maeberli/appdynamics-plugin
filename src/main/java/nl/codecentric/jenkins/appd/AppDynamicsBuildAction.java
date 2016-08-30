package nl.codecentric.jenkins.appd;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kohsuke.stapler.StaplerProxy;

import hudson.model.*;
import hudson.model.Action;
import hudson.util.StreamTaskListener;
import nl.codecentric.jenkins.appd.util.LocalMessages;

/**
 * The build action is defined as {@link Action}, and will fail the build should
 * the response of the system get too low, or when other conditions are not
 * met.<br />
 * <br />
 * The {@link AppDynamicsBuildAction} relays output and displaying of the build
 * output results to the {@link BuildActionResultsDisplay}.
 */
public class AppDynamicsBuildAction implements Action, StaplerProxy {

	private final AbstractBuild<?, ?> build;
	private final AppDynamicsReport report;
	private transient WeakReference<BuildActionResultsDisplay> buildActionResultsDisplay;

	private transient static final Logger logger = Logger.getLogger(AppDynamicsBuildAction.class.getName());

	public AppDynamicsBuildAction(AbstractBuild<?, ?> build, AppDynamicsReport report) {
		this.build = build;
		this.report = report;
	}

	public String getIconFileName() {
		return "graph.gif";
	}

	public String getDisplayName() {
		return LocalMessages.BUILDACTION_DISPLAYNAME.toString();
	}

	public String getUrlName() {
		return "appdynamics-dashboard";
	}

	public BuildActionResultsDisplay getTarget() {
		return getBuildActionResultsDisplay();
	}

	public AbstractBuild<?, ?> getBuild() {
		return build;
	}

	public AppDynamicsReport getAppDynamicsReport() {
		return report;
	}

	public BuildActionResultsDisplay getBuildActionResultsDisplay() {
		BuildActionResultsDisplay buildDisplay = null;
		WeakReference<BuildActionResultsDisplay> wr = this.buildActionResultsDisplay;
		if (wr != null) {
			buildDisplay = wr.get();
			if (buildDisplay != null)
				return buildDisplay;
		}

		try {
			buildDisplay = new BuildActionResultsDisplay(this, StreamTaskListener.fromStdout());
		} catch (IOException e) {
			logger.log(Level.SEVERE, "Error creating new BuildActionResultsDisplay()", e);
		}
		this.buildActionResultsDisplay = new WeakReference<BuildActionResultsDisplay>(buildDisplay);
		return buildDisplay;
	}

	public void setBuildActionResultsDisplay(WeakReference<BuildActionResultsDisplay> buildActionResultsDisplay) {
		this.buildActionResultsDisplay = buildActionResultsDisplay;
	}
}
