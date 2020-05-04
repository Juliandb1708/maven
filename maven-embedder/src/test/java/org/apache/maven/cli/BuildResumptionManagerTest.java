package org.apache.maven.cli;

import org.apache.maven.execution.BuildFailure;
import org.apache.maven.execution.BuildSuccess;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.lifecycle.LifecycleExecutionException;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;
import org.junit.Before;
import org.junit.Test;

import java.util.Properties;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class BuildResumptionManagerTest
{
    private final BuildResumptionManager buildResumptionManager = new BuildResumptionManager();

    private MavenExecutionResult result;

    @Before
    public void before() {
        result = new DefaultMavenExecutionResult();
    }

    @Test
    public void resumeFromGetsDetermined()
    {
        MavenProject projectA = createSucceededMavenProject( "A" );
        MavenProject projectB = createFailedMavenProject( "B" );
        result.setTopologicallySortedProjects( asList( projectA, projectB ) );

        Properties properties = buildResumptionManager.determineResumptionProperties( result );

        assertThat( properties.get( "resumeFrom" ), is( "test:B" ) );
    }

    @Test
    public void resumeFromIsIgnoredWhenFirstProjectFails()
    {
        MavenProject projectA = createFailedMavenProject( "A" );
        MavenProject projectB = createMavenProject( "B" );
        result.setTopologicallySortedProjects( asList( projectA, projectB ) );

        Properties properties = buildResumptionManager.determineResumptionProperties( result );

        assertThat( properties.containsKey( "resumeFrom" ), is(false) );
    }

    @Test
    public void projectsSucceedingAfterFailedProjectsAreExcluded()
    {
        MavenProject projectA = createSucceededMavenProject( "A" );
        MavenProject projectB = createFailedMavenProject( "B" );
        MavenProject projectC = createSucceededMavenProject( "C" );
        result.setTopologicallySortedProjects( asList( projectA, projectB, projectC ) );

        Properties properties = buildResumptionManager.determineResumptionProperties( result );

        assertThat( properties.get( "excludedProjects" ), is("test:C") );
    }

    @Test
    public void projectsDependingOnFailedProjectsAreNotExcluded()
    {
        MavenProject projectA = createSucceededMavenProject( "A" );
        MavenProject projectB = createFailedMavenProject( "B" );
        MavenProject projectC = createSucceededMavenProject( "C" );
        projectC.setDependencies( singletonList( toDependency( projectB ) ) );
        result.setTopologicallySortedProjects( asList( projectA, projectB, projectC ) );

        Properties properties = buildResumptionManager.determineResumptionProperties( result );

        assertThat( properties.containsKey( "excludedProjects" ), is(false) );
    }

    @Test
    public void multipleExcludedProjectsAreCommaSeparated()
    {
        MavenProject projectA = createFailedMavenProject( "A" );
        MavenProject projectB = createSucceededMavenProject( "B" );
        MavenProject projectC = createSucceededMavenProject( "C" );
        result.setTopologicallySortedProjects( asList( projectA, projectB, projectC ) );

        Properties properties = buildResumptionManager.determineResumptionProperties( result );

        assertThat( properties.get( "excludedProjects" ), is( "test:B, test:C" ) );
    }

    private MavenProject createMavenProject( String artifactId )
    {
        MavenProject project = new MavenProject();
        project.setGroupId( "test" );
        project.setArtifactId( artifactId );
        return project;
    }

    private Dependency toDependency( MavenProject mavenProject )
    {
        Dependency dependency = new Dependency();
        dependency.setGroupId( mavenProject.getGroupId() );
        dependency.setArtifactId( mavenProject.getArtifactId() );
        dependency.setVersion( mavenProject.getVersion() );
        return dependency;
    }

    private MavenProject createSucceededMavenProject( String artifactId )
    {
        MavenProject project = createMavenProject( artifactId );
        result.addBuildSummary( new BuildSuccess( project, 0 ) );
        return project;
    }

    private MavenProject createFailedMavenProject( String artifactId )
    {
        MavenProject project = createMavenProject( artifactId );
        result.addBuildSummary( new BuildFailure( project, 0, new Exception() ) );
        result.addException( new LifecycleExecutionException( "", project ) );
        return project;
    }
}
