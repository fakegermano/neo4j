/*
 * Copyright (c) 2002-2020 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel.api.index;

import org.junit.jupiter.api.Test;

import org.neo4j.internal.schema.IndexDescriptor;
import org.neo4j.internal.schema.IndexOrder;
import org.neo4j.internal.schema.IndexPrototype;
import org.neo4j.internal.schema.SchemaDescriptor;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BridgingIndexProgressorTest
{
    // We make sure to load this class here before we fork the tests for parallel execution.
    // The reason for this is we often deadlock on class loading together with DefaultNonUniqueIndexSamplerTest.
    private static final IndexDescriptor index = IndexPrototype.forSchema( SchemaDescriptor.forLabel( 1, 2, 3 ) ).withName( "a" ).materialise( 0 );

    @Test
    void closeMustCloseAll()
    {
        BridgingIndexProgressor progressor = new BridgingIndexProgressor( null, index.schema().getPropertyIds() );

        IndexProgressor[] parts = {mock(IndexProgressor.class), mock(IndexProgressor.class)};

        // Given
        for ( IndexProgressor part : parts )
        {
            progressor.initialize( index, part, null, IndexOrder.NONE, false, false );
        }

        // When
        progressor.close();

        // Then
        for ( IndexProgressor part : parts )
        {
            verify( part ).close();
        }
    }
}
