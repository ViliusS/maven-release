package org.apache.maven.shared.release.config;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.Properties;

import org.apache.maven.model.Scm;
import org.apache.maven.shared.release.scm.IdentifiedScm;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.AbstractLogEnabled;
import org.sonatype.plexus.components.cipher.DefaultPlexusCipher;
import org.sonatype.plexus.components.cipher.PlexusCipherException;
import org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcherException;
import org.sonatype.plexus.components.sec.dispatcher.SecUtil;
import org.sonatype.plexus.components.sec.dispatcher.model.SettingsSecurity;

/**
 * Read and write release configuration and state from a properties file.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 */
@Component( role = ReleaseDescriptorStore.class, hint = "properties" )
public class PropertiesReleaseDescriptorStore
    extends AbstractLogEnabled
    implements ReleaseDescriptorStore
{

    /**
     * When this plugin requires Maven 3.0 as minimum, this component can be removed and o.a.m.s.c.SettingsDecrypter be
     * used instead.
     */
    @Requirement( role = SecDispatcher.class, hint = "mng-4384" )
    private DefaultSecDispatcher secDispatcher;

    @Override
    public ReleaseDescriptor read( ReleaseDescriptor mergeDescriptor )
        throws ReleaseDescriptorStoreException
    {
        return read( mergeDescriptor, getDefaultReleasePropertiesFile( mergeDescriptor ) );
    }

    public ReleaseDescriptor read( File file )
        throws ReleaseDescriptorStoreException
    {
        return read( null, file );
    }

    public ReleaseDescriptor read( ReleaseDescriptor mergeDescriptor, File file )
        throws ReleaseDescriptorStoreException
    {
        Properties properties = new Properties();

        try ( InputStream inStream = new FileInputStream( file ) )
        {
            properties.load( inStream );
        }
        catch ( FileNotFoundException e )
        {
            getLogger().debug( file.getName() + " not found - using empty properties" );
        }
        catch ( IOException e )
        {
            throw new ReleaseDescriptorStoreException(
                "Error reading properties file '" + file.getName() + "': " + e.getMessage(), e );
        }

        ReleaseDescriptor releaseDescriptor = ReleaseUtils.copyPropertiesToReleaseDescriptor( properties );

        if ( mergeDescriptor != null )
        {
            releaseDescriptor = ReleaseUtils.merge( releaseDescriptor, mergeDescriptor );
        }

        return releaseDescriptor;
    }

    @Override
    public void write( ReleaseDescriptor config )
        throws ReleaseDescriptorStoreException
    {
        write( config, getDefaultReleasePropertiesFile( config ) );
    }

    @Override
    public void delete( ReleaseDescriptor config )
    {
        File file = getDefaultReleasePropertiesFile( config );
        if ( file.exists() )
        {
            file.delete();
        }
    }

    public void write( ReleaseDescriptor config, File file )
        throws ReleaseDescriptorStoreException
    {
        Properties properties = new Properties();
        properties.setProperty( "completedPhase", config.getCompletedPhase() );
        if ( config.isCommitByProject() ) //default is false
        {
            properties.setProperty( "commitByProject", "true" );
        }
        properties.setProperty( "scm.url", config.getScmSourceUrl() );
        if ( config.getScmId() != null )
        {
            properties.setProperty( "scm.id", config.getScmId() );
        }
        if ( config.getScmUsername() != null )
        {
            properties.setProperty( "scm.username", config.getScmUsername() );
        }
        if ( config.getScmPassword() != null )
        {
            String password = config.getScmPassword();
            try
            {
                password = encryptAndDecorate( password );
            }
            catch ( IllegalStateException | SecDispatcherException | PlexusCipherException e )
            {
                getLogger().debug( e.getMessage() );
            }
            properties.setProperty( "scm.password", password );
        }
        if ( config.getScmPrivateKey() != null )
        {
            properties.setProperty( "scm.privateKey", config.getScmPrivateKey() );
        }
        if ( config.getScmPrivateKeyPassPhrase() != null )
        {
            String passPhrase = config.getScmPrivateKeyPassPhrase();
            try
            {
                passPhrase = encryptAndDecorate( passPhrase );
            }
            catch ( IllegalStateException | SecDispatcherException | PlexusCipherException e )
            {
                getLogger().debug( e.getMessage() );
            }
            properties.setProperty( "scm.passphrase", passPhrase  );
        }
        if ( config.getScmTagBase() != null )
        {
            properties.setProperty( "scm.tagBase", config.getScmTagBase() );
        }
        if ( config.getScmBranchBase() != null )
        {
            properties.setProperty( "scm.branchBase", config.getScmBranchBase() );
        }
        if ( config.getScmReleaseLabel() != null )
        {
            properties.setProperty( "scm.tag", config.getScmReleaseLabel() );
        }
        if ( config.getScmTagNameFormat() != null )
        {
            properties.setProperty( "scm.tagNameFormat", config.getScmTagNameFormat() );
        }
        if ( config.getScmCommentPrefix() != null )
        {
            properties.setProperty( "scm.commentPrefix", config.getScmCommentPrefix() );
        }
        if ( config.getAdditionalArguments() != null )
        {
            properties.setProperty( "exec.additionalArguments", config.getAdditionalArguments() );
        }
        if ( config.getPomFileName() != null )
        {
            properties.setProperty( "exec.pomFileName", config.getPomFileName() );
        }
        if ( config.getPreparationGoals() != null )
        {
            properties.setProperty( "preparationGoals", config.getPreparationGoals() );
        }
        if ( config.getCompletionGoals() != null )
        {
            properties.setProperty( "completionGoals", config.getCompletionGoals() );
        }
        if ( config.getProjectVersionPolicyId() != null )
        {
            properties.setProperty( "projectVersionPolicyId", config.getProjectVersionPolicyId() );
        }
        if ( config.getProjectNamingPolicyId() != null )
        {
            properties.setProperty( "projectNamingPolicyId", config.getProjectNamingPolicyId() );
        }
        if ( config.getReleaseStrategyId() != null )
        {
            properties.setProperty( "releaseStrategyId", config.getReleaseStrategyId() );
        }

        properties.setProperty( "exec.snapshotReleasePluginAllowed",
                                Boolean.toString( config.isSnapshotReleasePluginAllowed() ) );

        properties.setProperty( "remoteTagging", Boolean.toString( config.isRemoteTagging() ) );

        properties.setProperty( "pushChanges", Boolean.toString( config.isPushChanges() ) );

        // others boolean properties are not written to the properties file because the value from the caller is always
        // used

        for ( Map.Entry<String, String> entry : config.getReleaseVersions().entrySet() )
        {
            properties.setProperty( "project.rel." + entry.getKey(), entry.getValue() );
        }

        for ( Map.Entry<String, String> entry : config.getDevelopmentVersions().entrySet() )
        {
            properties.setProperty( "project.dev." + entry.getKey(), (String) entry.getValue() );
        }

        for ( Map.Entry<String, Scm> entry : config.getOriginalScmInfo().entrySet() )
        {
            Scm scm = entry.getValue();
            String prefix = "project.scm." + entry.getKey();
            if ( scm != null )
            {
                if ( scm.getConnection() != null )
                {
                    properties.setProperty( prefix + ".connection", scm.getConnection() );
                }
                if ( scm.getDeveloperConnection() != null )
                {
                    properties.setProperty( prefix + ".developerConnection", scm.getDeveloperConnection() );
                }
                if ( scm.getUrl() != null )
                {
                    properties.setProperty( prefix + ".url", scm.getUrl() );
                }
                if ( scm.getTag() != null )
                {
                    properties.setProperty( prefix + ".tag", scm.getTag() );
                }
                if ( scm instanceof IdentifiedScm )
                {
                    IdentifiedScm identifiedScm = (IdentifiedScm) scm;
                    if ( identifiedScm.getId() != null )
                    {
                        properties.setProperty( prefix + ".id", identifiedScm.getId() );
                    }
                }
            }
            else
            {
                properties.setProperty( prefix + ".empty", "true" );
            }
        }

        if ( ( config.getResolvedSnapshotDependencies() != null )
            && ( config.getResolvedSnapshotDependencies().size() > 0 ) )
        {
            processResolvedDependencies( properties, config.getResolvedSnapshotDependencies() );
        }

        try ( OutputStream outStream = new FileOutputStream( file ) )
        {
            properties.store( outStream, "release configuration" );
        }
        catch ( IOException e )
        {
            throw new ReleaseDescriptorStoreException(
                "Error writing properties file '" + file.getName() + "': " + e.getMessage(), e );
        }
    }

    private void processResolvedDependencies( Properties prop, Map<String, Map<String, String>> resolvedDependencies )
    {
        for ( Map.Entry<String, Map<String, String>> currentEntry : resolvedDependencies.entrySet() )
        {
            Map<String, String> versionMap = currentEntry.getValue();
            
            prop.setProperty( "dependency." + currentEntry.getKey() + ".release",
                              versionMap.get( ReleaseDescriptor.RELEASE_KEY ) );
            prop.setProperty( "dependency." + currentEntry.getKey() + ".development",
                              versionMap.get( ReleaseDescriptor.DEVELOPMENT_KEY ) );
        }
    }

    private static File getDefaultReleasePropertiesFile( ReleaseDescriptor mergeDescriptor )
    {
        return new File( mergeDescriptor.getWorkingDirectory(), "release.properties" );
    }

    // From org.apache.maven.cli.MavenCli.encryption(CliRequest)
    private String encryptAndDecorate( String passwd )
        throws IllegalStateException, SecDispatcherException, PlexusCipherException
    {
        String configurationFile = secDispatcher.getConfigurationFile();

        if ( configurationFile.startsWith( "~" ) )
        {
            configurationFile = System.getProperty( "user.home" ) + configurationFile.substring( 1 );
        }

        String file = System.getProperty( DefaultSecDispatcher.SYSTEM_PROPERTY_SEC_LOCATION, configurationFile );

        String master = null;

        SettingsSecurity sec = SecUtil.read( file, true );
        if ( sec != null )
        {
            master = sec.getMaster();
        }

        if ( master == null )
        {
            throw new IllegalStateException( "Master password is not set in the setting security file: " + file );
        }

        DefaultPlexusCipher cipher = new DefaultPlexusCipher();
        String masterPasswd = cipher.decryptDecorated( master, DefaultSecDispatcher.SYSTEM_PROPERTY_SEC_LOCATION );
        return cipher.encryptAndDecorate( passwd, masterPasswd );
    }

}
