package org.apache.maven.model.composition;

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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.InputLocation;
import org.apache.maven.model.InputSource;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelProblemCollector;

/**
 * Handles the import of dependency management from other models into the target model.
 *
 * @author Benjamin Bentmann
 */
@Named
@Singleton
public class DefaultDependencyManagementImporter
    implements DependencyManagementImporter
{

    @Override
    public void importManagement( Model target, List<? extends DependencyManagement> sources,
                                  ModelBuildingRequest request, ModelProblemCollector problems )
    {
        if ( sources != null && !sources.isEmpty() )
        {
            Map<String, Dependency> dependencies = new LinkedHashMap<>();

            DependencyManagement depMgmt = target.getDependencyManagement();

            if ( depMgmt != null )
            {
                for ( Dependency dependency : depMgmt.getDependencies() )
                {
                    dependencies.put( dependency.getManagementKey(), dependency );
                }
            }
            else
            {
                depMgmt = new DependencyManagement();
                target.setDependencyManagement( depMgmt );
            }

            for ( DependencyManagement source : sources )
            {
                for ( Dependency dependency : source.getDependencies() )
                {
                    String key = dependency.getManagementKey();
                    if ( !dependencies.containsKey( key ) )
                    {
                        dependencies.put( key, dependency );
                        if ( request.isLocationTracking() )
                        {
                            updateDependencyHierarchy( dependency, source );
                        }
                    }
                }
            }

            depMgmt.setDependencies( new ArrayList<>( dependencies.values() ) );
        }
    }

    static void updateDependencyHierarchy( Dependency dependency, DependencyManagement bom )
    {
        // We are only interested in the InputSource, so the location of the <dependency> element is sufficient
        InputLocation dependencyLocation = dependency.getLocation( "" );
        InputLocation bomLocation = bom.getLocation( "" );

        if ( dependencyLocation == null || bomLocation == null )
        {
            return;
        }

        InputSource hierarchicalSource = dependencyLocation.getSource();
        InputSource bomSource = bomLocation.getSource();

        // Skip if the dependency and bom have the same source
        if ( hierarchicalSource == null || bomSource == null || Objects.equals( hierarchicalSource.getModelId(),
                bomSource.getModelId() ) )
        {
            return;
        }

        while ( hierarchicalSource.getImportedBy() != null )
        {
            InputSource newSource = hierarchicalSource.getImportedBy();

            // Stop if the bom is already in the list, no update necessary
            if ( Objects.equals( newSource.getModelId(), bomSource.getModelId() ) )
            {
                return;
            }

            hierarchicalSource = newSource;
        }

        // We modify the InputSource that is used for the whole file
        // This is assumed to be correct because the pom hierarchy applies to the whole pom, not just one dependency
        hierarchicalSource.setImportedBy( bomSource );
    }

}
