package org.bravo5.maven.plugins.releasehelper;

// http://docs.codehaus.org/display/MAVENUSER/Mojo+Developer+Cookbook

// http://maven.apache.org/ref/2.0.9/maven-artifact/apidocs/index.html
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;

// http://maven.apache.org/ref/2.0.9/maven-plugin-api/apidocs/index.html
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

// http://maven.apache.org/ref/2.0.9/maven-project/apidocs/index.html
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.artifact.InvalidDependencyVersionException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.util.Map;
import java.util.Set;
import java.util.List;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Generates JSON describing the -SNAPSHOT dependencies and parents in the
 * current reactor.
 *
 * @goal helper
 * @execute phase="validate"
 * @aggregator
 * @requiresDependencyResolution test
 * @requiresProject true
 */
public class ReleaseHelperMojo extends AbstractMojo {
    /**
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    protected MavenProject rootProject;

    /**
     * The projects in the reactor.
     *
     * @parameter expression="${reactorProjects}"
     * @readonly
     */
    private List<MavenProject> reactorProjects;

    /**
     * @parameter default-value="${localRepository}"
     * @required
     * @readonly
     */
    private ArtifactRepository localRepository;

    /**
     * @component
     * @required
     * @readonly
     */
    private MavenProjectBuilder mavenProjectBuilder;

    /**
     * @component
     * @required
     * @readonly
     */
    private ArtifactFactory artifactFactory;
    
    /**    
     * @parameter expression="${outputFile}"
     */
    private File outputFile;

    // Jackson JSON serializer
    private ObjectMapper objectMapper;
    
    // the fruits of our efforts
    private ObjectNode jsonDeps;
    
    // {{{ constructor
    public ReleaseHelperMojo() {
        objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    }
    // }}}
    
    // {{{ execute
    /** {@inheritDoc} */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        jsonDeps = objectMapper.createObjectNode();
        
        try {
            // because @aggregator, walk all projects in the reactor
            for (MavenProject p : reactorProjects) {
                visit(p);
            }

            if (outputFile != null) {
                objectMapper.writeValue(outputFile, jsonDeps);
            } else {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                objectMapper.writeValue(baos, jsonDeps);
                getLog().info(baos.toString());
            }
        } catch (InvalidDependencyVersionException e) {
            getLog().error("invalid dependency version somewhere", e);
        } catch (ProjectBuildingException e) {
            getLog().error("unable to build a project somewhere", e);
        } catch (IOException e) {
            throw new MojoExecutionException("IOE", e);
        }
        
        jsonDeps = null; // just in case
    }
    // }}}

    // {{{ visit
    private void visit(final MavenProject proj)
        throws InvalidDependencyVersionException, ProjectBuildingException
    {
        final String projKey = String.format("%s:%s", proj.getGroupId(), proj.getArtifactId());
        
        getLog().info("visiting project " + projKey);
        
        if (! proj.getArtifact().isSnapshot()) {
            getLog().info(projKey + " is not snapshot");
            return;
        }
        
        if (jsonDeps.get(projKey) == null) {
            ObjectNode jsonProj = jsonDeps.putObject(projKey);
            
            jsonProj.put("groupId", proj.getGroupId());
            jsonProj.put("artifactId", proj.getArtifactId());
            
            MavenProject parent = proj.getParent();
            if (parent == null) {
                jsonProj.putNull("parent");
            } else {
                visit(parent);
                
                /*
                "parent": {
                    "groupId": "com.myco",
                    "artifactId": "webservices",
                    "version": "12.12.19-SNAPSHOT",
                    "snapshot": true
                }
                */
                ObjectNode jsonParent = jsonProj.putObject("parent");
                jsonParent.put("groupId", parent.getGroupId());
                jsonParent.put("artifactId", parent.getArtifactId());
                jsonParent.put("version", parent.getVersion());
                jsonParent.put("snapshot", parent.getArtifact().isSnapshot());
            }
            
            // list of properties that look like they might be for SNAPSHOT
            // versions
            /*
            "snapshotProperties": {
                "contracts-version": "12.12.19-SNAPSHOT",
                "properties-version": "12.12.19.1-SNAPSHOT"
            }
            */
            ObjectNode jsonProps = jsonProj.putObject("snapshotProperties");
            if (proj.getProperties() != null) {
                for (Map.Entry<Object,Object> entry : proj.getProperties().entrySet()) {
                    String key = (String) entry.getKey();
                    String val = (String) entry.getValue();
                    
                    if (val.endsWith("-SNAPSHOT")) {
                        jsonProps.put(key, val);
                    }
                }
            }
            
            /*
            "snapshotDependencies": []
            */            
            ArrayNode depArr = jsonProj.putArray("snapshotDependencies");
            
            Set<Artifact> depArtifacts = proj.createArtifacts(artifactFactory, null, null);

            if (depArtifacts == null) {
                getLog().info("no dependency artifacts for " + projKey);
            } else {
                for (Artifact dep : depArtifacts) {
                    if (dep.isSnapshot()) {
                        final String depKey =
                            String.format("%s:%s", dep.getGroupId(), dep.getArtifactId());
                        
                        getLog().info(String.format("%s -> %s", projKey, depKey));
                        
                        /*
                        {
                            "groupId": "com.myco.commons",
                            "artifactId": "service",
                            "version": "12.7.13.1-SNAPSHOT"
                        }
                        */
                        ObjectNode jsonDep = depArr.addObject();
                        jsonDep.put("groupId", dep.getGroupId());
                        jsonDep.put("artifactId", dep.getArtifactId());
                        jsonDep.put("version", dep.getBaseVersion());
                        
                        visit(
                            mavenProjectBuilder.buildFromRepository(
                                dep,
                                rootProject.getRemoteArtifactRepositories(),
                                localRepository,
                                false
                            )
                        );
                    }
                }
            }
        } else {
            getLog().info("already visited " + projKey);
        }
    }
    // }}}
}
