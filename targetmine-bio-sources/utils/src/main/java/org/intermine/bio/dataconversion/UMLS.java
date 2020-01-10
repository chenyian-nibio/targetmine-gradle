package org.intermine.bio.dataconversion;

public class UMLS {
	private String identifier;
	private String name;
	private String semanticType;
	private String dbType;
	private String dbId;
	public UMLS() {
		
	}
	public UMLS(String identifier, String name, String semanticType,String dbType,String dbId) {
		super();
		this.identifier = identifier;
		this.name = name;
		this.semanticType = semanticType;
		this.dbType = dbType;
		this.dbId = dbId;
	}
	public String getIdentifier() {
		return identifier;
	}
	public String getName() {
		return name;
	}
	public String getSemanticType() {
		return semanticType;
	}
	public String getDbType() {
		return dbType;
	}
	public String getDbId() {
		return dbId;
	}
	
}
