package nl.codecentric.jenkins.appd.rest.types;

/**
 * POJO object for unmarshalling JSON data from the AppDynamics REST interface.
 * Maps to the following structure:
 * 
 * Application: { "description": "", "id": 9, "name": "CRM2" }
 */
public class ApplicationInformation {

	private String description;
	private int id;
	private String name;

	public final String getDescription() {
		return description;
	}

	public final void setDescription(final String description) {
		this.description = description;
	}

	public final int getId() {
		return id;
	}

	public final void setId(final int id) {
		this.id = id;
	}

	public final String getName() {
		return name;
	}

	public final void setName(final String name) {
		this.name = name;
	}

}
