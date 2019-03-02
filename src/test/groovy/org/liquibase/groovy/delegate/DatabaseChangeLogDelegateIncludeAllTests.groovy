/*
 * Copyright 2011-2019 Tim Berglund and Steven C. Saliman
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.liquibase.groovy.delegate

import liquibase.changelog.ChangeLogParameters
import liquibase.changelog.DatabaseChangeLog
import liquibase.exception.ChangeLogParseException
import liquibase.parser.ChangeLogParserFactory
import liquibase.parser.ext.GroovyLiquibaseChangeLogParser
import liquibase.precondition.core.DBMSPrecondition
import liquibase.precondition.core.PreconditionContainer
import liquibase.precondition.core.RunningAsPrecondition
import liquibase.resource.ClassLoaderResourceAccessor
import liquibase.resource.FileSystemResourceAccessor
import org.junit.Before
import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertNull
import static org.junit.Assert.assertTrue
import static org.junit.Assert.fail

/**
 * One of three test classes for the {@link DatabaseChangeLogDelegate}.  The
 * number of tests for {@link DatabaseChangeLogDelegate} were getting unwieldy,
 * so they were split up.  this class deals with all the "includeAll" element of
 * a database changelog.
 *
 * @author Steven C. Saliman
 */
class DatabaseChangeLogDelegateIncludeAllTests {
	// Let's define some paths and directories.  These should all be relative.
	static final ROOT_CHANGELOG_PATH = "src/test/changelog"
	static final TMP_CHANGELOG_PATH = ROOT_CHANGELOG_PATH + "/tmp"
	static final INCLUDED_CHANGELOG_PATH = TMP_CHANGELOG_PATH + "/include"
	static final TMP_CHANGELOG_DIR = new File(TMP_CHANGELOG_PATH)
	static final INCLUDED_CHANGELOG_DIR = new File(INCLUDED_CHANGELOG_PATH)
	static final ROOT_CHANGE_SET = 'root-change-set'
	static final FIRST_INCLUDED_CHANGE_SET = 'included-change-set-1'
	static final SECOND_INCLUDED_CHANGE_SET = 'included-change-set-2'

	def resourceAccessor
	ChangeLogParserFactory parserFactory


	@Before
	void registerParser() {
		resourceAccessor = new FileSystemResourceAccessor()
		parserFactory = ChangeLogParserFactory.instance
		ChangeLogParserFactory.getInstance().register(new GroovyLiquibaseChangeLogParser())
		// make sure we start with clean temporary directories before each test
		TMP_CHANGELOG_DIR.deleteDir()
		INCLUDED_CHANGELOG_DIR.mkdirs()
	}

	/**
	 * Test including a path when we have an unsupported attribute.
	 */
	@Test(expected = ChangeLogParseException)
	void includeAllInvalidAttribute() {
		buildChangeLog {
			includeAll(changePath: 'invalid')
		}
	}

	/**
	 * Try including all files in a directory.  For this test, we want a path
	 * that contains an invalid token.  The easiest way to do that is to
	 * simply use a token that doesn't have a matching property.
	 */
	@Test(expected = ChangeLogParseException)
	void includeAllWithInvalidProperty() {
		def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  includeAll(path: '\${includedChangeLogDir}')
  changeSet(author: 'ssaliman', id: '${ROOT_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")
		def parser = parserFactory.getParser(rootChangeLogFile.path, resourceAccessor)
		parser.parse(rootChangeLogFile.path, new ChangeLogParameters(), resourceAccessor)
	}

	/**
	 * Try including all files in a directory.  For this test, we want 2 files
	 * to make sure we include them both, and in the right order.  This test
	 * makes sure that tokens don't affect absolute paths.
	 * <p>
	 * Note: when other tests throw exceptions, this test may also fail because
	 * of unclean directories.  Fix the other tests first.
	 */
	@Test
	void includeAllWithValidToken() {
		def includedChangeLogDir = createIncludedChangeLogFiles()

		def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  property(name: 'includeDir', value: '${includedChangeLogDir}')
  includeAll(path: '\${includeDir}')
  changeSet(author: 'ssaliman', id: '${ROOT_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")
		// Use an absolute path for the root changelog to prove that we can.
		def parser = parserFactory.getParser(rootChangeLogFile.absolutePath, resourceAccessor)
		def rootChangeLog = parser.parse(rootChangeLogFile.absolutePath, new ChangeLogParameters(), resourceAccessor)

		assertNotNull rootChangeLog
		def changeSets = rootChangeLog.changeSets
		assertNotNull changeSets
		assertEquals 3, changeSets.size()
		assertEquals FIRST_INCLUDED_CHANGE_SET, changeSets[0].id
		assertEquals SECOND_INCLUDED_CHANGE_SET, changeSets[1].id
		assertEquals ROOT_CHANGE_SET, changeSets[2].id

		// Make sure we started with an absolute path, then make sure all 3
		// change sets have absolute paths
		assertAbsolutePath includedChangeLogDir
		assertAbsolutePath changeSets[0].filePath
		assertAbsolutePath changeSets[1].filePath
		assertAbsolutePath changeSets[2].filePath

		verifyIncludedPreconditions rootChangeLog.preconditionContainer?.nestedPreconditions
	}

	/**
	 * Try including all files in a directory.  For this test, we want 2 files
	 * to make sure we include them both, and in the right order.  This test
	 * does things with absolute paths so we can verify that the DSL preserves
	 * the absolute paths.
	 * <p>
	 * This test will also make sure we can handle contexts properly.
	 * <p>
	 * Note: when other tests throw exceptions, this test may also fail because
	 * of unclean directories.  Fix the other tests first.
	 */
	@Test
	void includeAllAbsolutePath() {
		def includedChangeLogDir = createIncludedChangeLogFiles()

		def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  includeAll(path: '${includedChangeLogDir}', context: 'myContext')
  changeSet(author: 'ssaliman', id: '${ROOT_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")
		def parser = parserFactory.getParser(rootChangeLogFile.path, resourceAccessor)
		def rootChangeLog = parser.parse(rootChangeLogFile.path, new ChangeLogParameters(), resourceAccessor)

		assertNotNull rootChangeLog
		def changeSets = rootChangeLog.changeSets
		assertNotNull changeSets
		assertEquals 3, changeSets.size()
		assertEquals FIRST_INCLUDED_CHANGE_SET, changeSets[0].id
		assertEquals SECOND_INCLUDED_CHANGE_SET, changeSets[1].id
		assertEquals ROOT_CHANGE_SET, changeSets[2].id

		// Make sure we actually started with an absolute path, then check the
		// the paths of the included change sets.  The first two, which came
		// from the included file should be are absolute.  The 3rd one, which
		// came from the root changelog should be relative.
		assertAbsolutePath includedChangeLogDir
		assertAbsolutePath changeSets[0].filePath
		assertAbsolutePath changeSets[1].filePath
		assertTrue changeSets[2].filePath.startsWith(TMP_CHANGELOG_PATH)

		// Take a look at the contexts of the changes.  The first 2, came from
		// the included file, and should have contexts.  The 3rd one came from
		// the root changelog and should not.
		assertEquals 'myContext', changeSets[0].changeLog.includeContexts.toString()
		assertEquals 'myContext', changeSets[1].changeLog.includeContexts.toString()
		assertNull changeSets[2].changeLog.includeContexts

		verifyIncludedPreconditions rootChangeLog.preconditionContainer?.nestedPreconditions
	}

	/**
	 * Try including all files in a directory.  For this test, we want 2 files
	 * to make sure we include them both, and in the right order.  This test
	 * does things with relative paths so we can verify that the DSL preserves
	 * the relative paths instead of converting them to absolute paths.
	 * <p>
   	 * This test will also make sure we can handle contexts properly.
	 * <p>
	 * Note: when other tests throw exceptions, this test may also fail because
	 * of unclean directories.  Fix the other tests first.
	 */
	@Test
	void includeAllRelativeToWorkDir() {
		createIncludedChangeLogFiles()

		def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  includeAll(path: '${INCLUDED_CHANGELOG_PATH}')
  changeSet(author: 'ssaliman', id: '${ROOT_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")
		def parser = parserFactory.getParser(rootChangeLogFile.path, resourceAccessor)
		def rootChangeLog = parser.parse(rootChangeLogFile.path, new ChangeLogParameters(), resourceAccessor)

		assertNotNull rootChangeLog
		def changeSets = rootChangeLog.changeSets
		assertNotNull changeSets
		assertEquals 3, changeSets.size()
		assertEquals FIRST_INCLUDED_CHANGE_SET, changeSets[0].id
		assertEquals SECOND_INCLUDED_CHANGE_SET, changeSets[1].id
		assertEquals ROOT_CHANGE_SET, changeSets[2].id

		// Check that the paths of the 2 included change sets are relative.
		// The 3rd change set did not come from the "includeAll", but it will
		// be relative.
		assertTrue changeSets[0].filePath.startsWith(INCLUDED_CHANGELOG_PATH)
		assertTrue changeSets[1].filePath.startsWith(INCLUDED_CHANGELOG_PATH)
		assertTrue changeSets[2].filePath.startsWith(TMP_CHANGELOG_PATH)

		verifyIncludedPreconditions rootChangeLog.preconditionContainer?.nestedPreconditions
	}

	/**
	 * Try including all files in a directory relative to a changelog that uses
	 * an absolute path. This test is looking at the relativeToChangeLogFile
	 * parameter.
	 * <p>
	 * At the moment, if you start with an absolute changelog, all the included
	 * changes will have absolute paths, even when they are supposed to be
	 * relative to the changelog.  This is an internal liquibase issue.  If
	 * liquibase ever changes how it does things, this test may need to change.
	 */
	@Test
	void includeAllRelativeToAbsoluteChangeLog() {
		createIncludedChangeLogFiles()
		def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  includeAll(path: 'include', relativeToChangelogFile: true)
  changeSet(author: 'ssaliman', id: '${ROOT_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")
		// This one is absolute...
		def parser = parserFactory.getParser(rootChangeLogFile.absolutePath, resourceAccessor)
		def rootChangeLog = parser.parse(rootChangeLogFile.absolutePath, new ChangeLogParameters(), resourceAccessor)

		assertNotNull rootChangeLog
		def changeSets = rootChangeLog.changeSets
		assertNotNull changeSets
		assertEquals 3, changeSets.size()
		assertEquals FIRST_INCLUDED_CHANGE_SET, changeSets[0].id
		assertEquals SECOND_INCLUDED_CHANGE_SET, changeSets[1].id
		assertEquals ROOT_CHANGE_SET, changeSets[2].id

		assertAbsolutePath changeSets[0].filePath
		assertAbsolutePath changeSets[1].filePath
		assertAbsolutePath changeSets[2].filePath

		verifyIncludedPreconditions rootChangeLog.preconditionContainer?.nestedPreconditions
	}

	/**
	 * Try including all files in a directory relative to a changelog that uses
	 * a relative path. This test is looking at the relativeToChangeLogFile
	 * parameter.
	 */
	@Test
	void includeAllRelativeToRelativeChangeLog() {
		createIncludedChangeLogFiles()
		def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  includeAll(path: 'include', relativeToChangelogFile: true)
  changeSet(author: 'ssaliman', id: '${ROOT_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")
		def parser = parserFactory.getParser(rootChangeLogFile.path, resourceAccessor)
		def rootChangeLog = parser.parse(rootChangeLogFile.path, new ChangeLogParameters(), resourceAccessor)

		assertNotNull rootChangeLog
		def changeSets = rootChangeLog.changeSets
		assertNotNull changeSets
		assertEquals 3, changeSets.size()
		assertEquals FIRST_INCLUDED_CHANGE_SET, changeSets[0].id
		assertEquals SECOND_INCLUDED_CHANGE_SET, changeSets[1].id
		assertEquals ROOT_CHANGE_SET, changeSets[2].id

		// Make sure we actually started with an absolute path
		// Check that the paths of the 2 included change sets are relative.
		// The 3rd change set did not come from the "includeAll", but it will
		// be relative.
		assertTrue changeSets[0].filePath.startsWith(INCLUDED_CHANGELOG_PATH)
		assertTrue changeSets[1].filePath.startsWith(INCLUDED_CHANGELOG_PATH)
		assertTrue changeSets[2].filePath.startsWith(TMP_CHANGELOG_PATH)

		verifyIncludedPreconditions rootChangeLog.preconditionContainer?.nestedPreconditions
	}

	/**
	 * Try including all files in a directory relative to a changelog that uses
	 * a relative path. This test is looking at the relativeToChangeLogFile
	 * parameter.  For this test, the included changelogs are not in the same
	 * directory as (or a subdirectory of) the root changelog.  The main thing
	 * here is to make sure paths like "../somedir" work.
	 */
	@Test
	void includeAllRelativeToRelativeChangeLogParent() {
		createIncludedChangeLogFiles()
		def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  includeAll(path: '../tmp/include', relativeToChangelogFile: true)
  changeSet(author: 'ssaliman', id: '${ROOT_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")
		def parser = parserFactory.getParser(rootChangeLogFile.path, resourceAccessor)
		def rootChangeLog = parser.parse(rootChangeLogFile.path, new ChangeLogParameters(), resourceAccessor)

		assertNotNull rootChangeLog
		def changeSets = rootChangeLog.changeSets
		assertNotNull changeSets
		assertEquals 3, changeSets.size()
		assertEquals FIRST_INCLUDED_CHANGE_SET, changeSets[0].id
		assertEquals SECOND_INCLUDED_CHANGE_SET, changeSets[1].id
		assertEquals ROOT_CHANGE_SET, changeSets[2].id

		// Check that the paths of the 2 included change sets are relative.
		// The 3rd change set did not come from the "includeAll", but it will
		// be relative.
		assertTrue changeSets[0].filePath.startsWith(INCLUDED_CHANGELOG_PATH)
		assertTrue changeSets[1].filePath.startsWith(INCLUDED_CHANGELOG_PATH)
		assertTrue changeSets[2].filePath.startsWith(TMP_CHANGELOG_PATH)

		verifyIncludedPreconditions rootChangeLog.preconditionContainer?.nestedPreconditions
	}

	/**
	 * Try including all files in a directory, but with the deprecated
	 * resourceFilter attribute. In the future, this test will go away, but
	 * for now, we'll support For this test, we'll repeat want 2 files, but with a filter that
	 * excludes one of them. Test may fail because of unclean directories.
	 * Fix the other tests first.
	 */
	@Test(expected = ChangeLogParseException)
	void includeAllValidWithResourceFilter() {
		createIncludedChangeLogFiles()

		def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  includeAll(path: '${INCLUDED_CHANGELOG_PATH}',
             resourceFilter: 'org.liquibase.groovy.helper.IncludeAllFirstOnlyFilter')
  changeSet(author: 'ssaliman', id: '${ROOT_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")
		def parser = parserFactory.getParser(rootChangeLogFile.path, resourceAccessor)
		parser.parse(rootChangeLogFile.path, new ChangeLogParameters(), resourceAccessor)
	}

	/**
	 * Try including all files in a directory, but with a resource filter.
	 * For this test, we'll repeat want 2 files, but with a filter that
	 * excludes one of them. Test may fail because of unclean directories.
	 * Fix the other tests first.
	 */
	@Test
	void includeAllValidWithFilter() {
		createIncludedChangeLogFiles()

		def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  includeAll(path: '${INCLUDED_CHANGELOG_PATH}',
             filter: 'org.liquibase.groovy.helper.IncludeAllFirstOnlyFilter')
  changeSet(author: 'ssaliman', id: '${ROOT_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")
		def parser = parserFactory.getParser(rootChangeLogFile.path, resourceAccessor)
		def rootChangeLog = parser.parse(rootChangeLogFile.path, new ChangeLogParameters(), resourceAccessor)

		assertNotNull rootChangeLog
		def changeSets = rootChangeLog.changeSets
		assertNotNull changeSets
		assertEquals 2, changeSets.size()  // from the first file, and the changelog itself.
		assertEquals FIRST_INCLUDED_CHANGE_SET, changeSets[0].id
		assertEquals ROOT_CHANGE_SET, changeSets[1].id

		assertTrue changeSets[0].filePath.startsWith(INCLUDED_CHANGELOG_PATH)
		assertTrue changeSets[1].filePath.startsWith(TMP_CHANGELOG_PATH)

		def preconditions = rootChangeLog.preconditionContainer?.nestedPreconditions
		assertNotNull preconditions
		assertEquals 2, preconditions.size()
		assertTrue preconditions[0] instanceof DBMSPrecondition
		assertTrue preconditions[1] instanceof PreconditionContainer
		preconditions = preconditions[1].nestedPreconditions
		assertTrue preconditions[0] instanceof RunningAsPrecondition
	}

	/**
	 * Try including all files in a directory with an invalid resource filter.
	 * For this test, we'll use a class that doesn't exist.
	 */
	@Test(expected = ChangeLogParseException)
	void includeAllValidWithInvalidFilter() {
		createIncludedChangeLogFiles()

		def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  includeAll(path: '${INCLUDED_CHANGELOG_PATH}',
             filter: 'org.liquibase.groovy.helper.NoSuchClass')
  changeSet(author: 'ssaliman', id: '${ROOT_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")
		def parser = parserFactory.getParser(rootChangeLogFile.path, resourceAccessor)
		parser.parse(rootChangeLogFile.path, new ChangeLogParameters(), resourceAccessor)
	}

	/**
	 * Try including all files in a directory with an invalid resource filter.
	 * For this test, we'll use a class that exists, but isn't an
	 * {@code IncludeAllFilter}.
	 */
	@Test(expected = ChangeLogParseException)
	void includeAllValidWithInappropriateFilter() {
		createIncludedChangeLogFiles()

		def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  includeAll(path: '${INCLUDED_CHANGELOG_PATH}',
             filter: 'org.liquibase.groovy.helper.ReversingComparator')
  changeSet(author: 'ssaliman', id: '${ROOT_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")
		def parser = parserFactory.getParser(rootChangeLogFile.path, resourceAccessor)
		parser.parse(rootChangeLogFile.path, new ChangeLogParameters(), resourceAccessor)
	}

	/**
	 * Try including all files in a directory when we provide a custom
	 * comparator.  For this test, we'll repeat includeAllRelativeToWorkDir,
	 * but with a comparator that sorts the included changes in reverse
	 * alphabetical order.  We still expect 2 files, but this time, they should
	 * come in to the changes in reverse order.
	 * <p>
	 * This test will also make sure we can handle contexts properly.
	 * <p>
	 * Note: when other tests throw exceptions, this test may also fail because
	 * of unclean directories.  Fix the other tests first.
	 */
	@Test
	void includeAllWithComparator() {
		createIncludedChangeLogFiles()

		def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  includeAll(path: '${INCLUDED_CHANGELOG_PATH}',
             resourceComparator: 'org.liquibase.groovy.helper.ReversingComparator')
  changeSet(author: 'ssaliman', id: '${ROOT_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")
		def parser = parserFactory.getParser(rootChangeLogFile.path, resourceAccessor)
		def rootChangeLog = parser.parse(rootChangeLogFile.path, new ChangeLogParameters(), resourceAccessor)

		assertNotNull rootChangeLog
		def changeSets = rootChangeLog.changeSets
		assertNotNull changeSets
		assertEquals 3, changeSets.size()
		// Remember, these should come in BACKWARDS because of the comparator.
		assertEquals SECOND_INCLUDED_CHANGE_SET, changeSets[0].id
		assertEquals FIRST_INCLUDED_CHANGE_SET, changeSets[1].id
		assertEquals ROOT_CHANGE_SET, changeSets[2].id

		// Check that the paths of the 2 included change sets are relative.
		// The 3rd change set did not come from the "includeAll", but it will
		// be relative.
		assertTrue changeSets[0].filePath.startsWith(INCLUDED_CHANGELOG_PATH)
		assertTrue changeSets[1].filePath.startsWith(INCLUDED_CHANGELOG_PATH)
		assertTrue changeSets[2].filePath.startsWith(TMP_CHANGELOG_PATH)

		// We can't use the standard precondition validator because the order
		// changed.
		def preconditions = rootChangeLog.preconditionContainer?.nestedPreconditions
		assertEquals 3, preconditions.size()
		assertTrue preconditions[0] instanceof DBMSPrecondition
		assertTrue preconditions[1] instanceof PreconditionContainer
		def nested = preconditions[1].nestedPreconditions
		assertEquals 0, nested.size()
		nested = preconditions[2].nestedPreconditions
		assertTrue nested[0] instanceof RunningAsPrecondition
		assertTrue preconditions[2] instanceof PreconditionContainer

	}

	/**
	 * Try including all files in a directory when we provide an invalid custom
	 * comparator.  For this test, we'll use a class that doesn't exist.
	 */
	@Test(expected = ChangeLogParseException)
	void includeAllWithInvalidComparator() {
		createIncludedChangeLogFiles()

		def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  includeAll(path: '${INCLUDED_CHANGELOG_PATH}',
             resourceComparator: 'org.liquibase.groovy.helper.NoSuchClass')
  changeSet(author: 'ssaliman', id: '${ROOT_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")
		def parser = parserFactory.getParser(rootChangeLogFile.path, resourceAccessor)
		parser.parse(rootChangeLogFile.path, new ChangeLogParameters(), resourceAccessor)
	}

	/**
	 * Try including all files in a directory when we provide an invalid custom
	 * comparator.  For this test, we'll use a class that does exist, but is
	 * not a {@code Comparator}
	 */
	@Test(expected = ChangeLogParseException)
	void includeAllWithInappropriateComparator() {
		createIncludedChangeLogFiles()

		def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  includeAll(path: '${INCLUDED_CHANGELOG_PATH}',
             resourceComparator: 'org.liquibase.groovy.helper.IncludeAllFirstOnlyFilter')
  changeSet(author: 'ssaliman', id: '${ROOT_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")
		def parser = parserFactory.getParser(rootChangeLogFile.path, resourceAccessor)
		parser.parse(rootChangeLogFile.path, new ChangeLogParameters(), resourceAccessor)
	}

	/**
	 * Try including all when the path doesn't exist is invalid.  Expect an error.
	 */
	@Test(expected = ChangeLogParseException)
	void includeAllInvalidPath() {
		buildChangeLog {
			includeAll(path: 'invalid')
		}
	}

	/**
	 * Try including all when the path doesn't exist is invalid, but we've
	 * set the errorIfMissingOrEmpty property to false.  For this test, we'll
	 * use a string to represent falseness.
	 */
	@Test
	void includeAllInvalidPathIgnoreError() {
		def changeLog = buildChangeLog {
			includeAll(path: 'invalid', errorIfMissingOrEmpty: false)
		}
		assertNotNull changeLog
		def changeSets = changeLog.changeSets
		assertNotNull changeSets
		assertEquals 0, changeSets.size()
	}

	/**
	 * Try including all when the path is valid, but there are no usable files
	 * in the directory.  We'll test this by using the filter to eliminate the
	 * one change set we'll create to make sure we do the test after the filter.
	 */
	@Test(expected = ChangeLogParseException)
	void includeAllEmptyPath() {
		// This file should be excluded by the resource filter.
		createFileFrom(INCLUDED_CHANGELOG_DIR, 'second', '-2.groovy', """
databaseChangeLog {
  changeSet(author: 'ssaliman', id: '${SECOND_INCLUDED_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(30)')
    }
  }
}
""")

		def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  includeAll(path: '${INCLUDED_CHANGELOG_PATH}',
             filter: 'org.liquibase.groovy.helper.IncludeAllFirstOnlyFilter')
  changeSet(author: 'ssaliman', id: '${ROOT_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")
		def parser = parserFactory.getParser(rootChangeLogFile.path, resourceAccessor)
		parser.parse(rootChangeLogFile.path, new ChangeLogParameters(), resourceAccessor)
	}

	/**
	 * Try including all when the path is valid, but there are no usable files
	 * in the directory.  This time, we'll set the errorIfMissingOrEmpty
	 * property to false.  For this test, we'll use a boolean to represent
	 * falseness.  We should ignore the error about the empty directory, and
	 * get the root change set from the parent file.
	 */
	@Test
	void includeAllEmptyPathIgnoreError() {
		// This file should be excluded by the resource filter.
		createFileFrom(INCLUDED_CHANGELOG_DIR, 'second', '-2.groovy', """
databaseChangeLog {
  changeSet(author: 'ssaliman', id: '${SECOND_INCLUDED_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(30)')
    }
  }
}
""")
		def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  includeAll(path: '${INCLUDED_CHANGELOG_PATH}', errorIfMissingOrEmpty: false,
             filter: 'org.liquibase.groovy.helper.IncludeAllFirstOnlyFilter')
  changeSet(author: 'ssaliman', id: '${ROOT_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")

		def parser = parserFactory.getParser(rootChangeLogFile.path, resourceAccessor)
		def rootChangeLog = parser.parse(rootChangeLogFile.path, new ChangeLogParameters(), resourceAccessor)

		assertNotNull rootChangeLog
		def changeSets = rootChangeLog.changeSets
		assertNotNull changeSets
		assertEquals 1, changeSets.size()  // from the changelog itself.
		assertEquals ROOT_CHANGE_SET, changeSets[0].id

		def preconditions = rootChangeLog.preconditionContainer?.nestedPreconditions
		assertNotNull preconditions
		assertEquals 1, preconditions.size()
		assertTrue preconditions[0] instanceof DBMSPrecondition
	}

	//----------------------------------------------------------------------
	// Tests of the includeAll method when the changelog file is accessed
	// via the classpath.

	/**
	 * Try including all files in a classpath directory.  We'll want to make
	 * sure we include them both, and in the right order.
	 * <p>
	 * The change logs can't be created on the fly, they must exist in a
	 * directory that is on the classpath, and we need to replace the resource
	 * accessor with one that can load a file from the classpath.
	 */
	@Test
	void includeAllClasspath() {
		def rootChangeLogFile = "changelog.groovy"
		resourceAccessor = new ClassLoaderResourceAccessor()

		def parser = parserFactory.getParser(rootChangeLogFile, resourceAccessor)
		def rootChangeLog = parser.parse(rootChangeLogFile, new ChangeLogParameters(), resourceAccessor)

		assertNotNull rootChangeLog
		def changeSets = rootChangeLog.changeSets
		assertNotNull changeSets
		assertEquals 3, changeSets.size()
		assertEquals FIRST_INCLUDED_CHANGE_SET, changeSets[0].id
		assertEquals SECOND_INCLUDED_CHANGE_SET, changeSets[1].id
		assertEquals ROOT_CHANGE_SET, changeSets[2].id

		// Verify our file paths.  The included changes should have relative
		// paths.  The change from the root file will have just the name of the
		// file because it was in the root of the classpath.  Remember, we're
		// using classpaths here, so our relative directories will be relative
		// to the classpath root.
		assertTrue changeSets[0].filePath.startsWith('include/')
		assertTrue changeSets[1].filePath.startsWith('include/')
		assertTrue changeSets[2].filePath.equals('changelog.groovy')

		verifyIncludedPreconditions rootChangeLog.preconditionContainer?.nestedPreconditions
	}

	/**
	 * Try including all files in a classpath directory, but with a resource
	 * filter. For this test, we'll have 2 files in the directory, but the
	 * resource filter will excludes one of them.
	 * <p>
	 * The change logs can't be created on the fly, it must exist in a directory
	 * that is on the classpath, and we need to replace the resource accessor
	 * with one that can load a file from the classpath.
	 */
	@Test
	void includeAllClasspathWithFilter() {
		def rootChangeLogFile = "filtered-changelog.groovy"
		resourceAccessor = new ClassLoaderResourceAccessor()

		def parser = parserFactory.getParser(rootChangeLogFile, resourceAccessor)
		def rootChangeLog = parser.parse(rootChangeLogFile, new ChangeLogParameters(), resourceAccessor)

		assertNotNull rootChangeLog
		def changeSets = rootChangeLog.changeSets
		assertNotNull changeSets
		assertEquals 2, changeSets.size()  // from the first file, and the changelog itself.
		assertEquals FIRST_INCLUDED_CHANGE_SET, changeSets[0].id
		assertEquals ROOT_CHANGE_SET, changeSets[1].id

		// Verify our file paths.  The included changes should have relative
		// paths.  The change from the root file will have just the name of the
		// file because it was in the root of the classpath.  Remember, we're
		// using classpaths here, so our relative directories will be relative
		// to the classpath root.
		assertTrue changeSets[0].filePath.startsWith('include/')
		assertTrue changeSets[1].filePath.equals('filtered-changelog.groovy')

		def preconditions = rootChangeLog.preconditionContainer?.nestedPreconditions
		assertNotNull preconditions
		assertEquals 2, preconditions.size()
		assertTrue preconditions[0] instanceof DBMSPrecondition
		assertTrue preconditions[1] instanceof PreconditionContainer
		preconditions = preconditions[1].nestedPreconditions
		assertTrue preconditions[0] instanceof RunningAsPrecondition
	}

	/**
	 * Try including all from a classpath loaded change log when the include
	 * path doesn't exist is invalid.  Expect an error.
	 * <p>
	 * The change logs can't be created on the fly, it must exist in a directory
	 * that is on the classpath, and we need to replace the resource accessor
	 * with one that can load a file from the classpath.
	 */
	@Test(expected = ChangeLogParseException)
	void includeAllInvalidClassPath() {
		def rootChangeLogFile = "invalid-changelog.groovy"
		resourceAccessor = new ClassLoaderResourceAccessor()

		def parser = parserFactory.getParser(rootChangeLogFile, resourceAccessor)
		parser.parse(rootChangeLogFile, new ChangeLogParameters(), resourceAccessor)
	}

	/**
	 * Try including all from a classpath loaded change log when the include
	 * path is invalid, but we've set the errorIfMissingOrEmpty property to
	 * false.
	 * <p>
	 * The change logs can't be created on the fly, it must exist in a directory
	 * that is on the classpath, and we need to replace the resource accessor
	 * with one that can load a file from the classpath.
	 */
	@Test
	void includeAllInvalidClassPathIgnoreError() {
		def rootChangeLogFile = "ignore-changelog.groovy"
		resourceAccessor = new ClassLoaderResourceAccessor()

		def parser = parserFactory.getParser(rootChangeLogFile, resourceAccessor)
		def rootChangeLog = parser.parse(rootChangeLogFile, new ChangeLogParameters(), resourceAccessor)

		assertNotNull rootChangeLog
		def changeSets = rootChangeLog.changeSets
		assertNotNull changeSets
		assertEquals 1, changeSets.size()  // from the first file, and the changelog itself.
		assertEquals ROOT_CHANGE_SET, changeSets[0].id

		assertTrue changeSets[0].filePath.equals('ignore-changelog.groovy')

		def preconditions = rootChangeLog.preconditionContainer?.nestedPreconditions
		assertNotNull preconditions
		assertEquals 1, preconditions.size()
		assertTrue preconditions[0] instanceof DBMSPrecondition
	}

	/**
	 * Helper method that builds a changeSet from the given closure.  Tests will
	 * use this to test parsing the various closures that make up the Groovy DSL.
	 * @param closure the closure containing changes to parse.
	 * @return the changeSet, with parsed changes from the closure added.
	 */
	private def buildChangeLog(Closure closure) {
		def changelog = new DatabaseChangeLog(ROOT_CHANGELOG_PATH)
		changelog.changeLogParameters = new ChangeLogParameters()
		closure.delegate = new DatabaseChangeLogDelegate(changelog)
		closure.delegate.resourceAccessor = resourceAccessor
		closure.resolveStrategy = Closure.DELEGATE_FIRST
		closure.call()
		return changelog
	}

	/**
	 * Helper method to create changelogs in a directory for testing the
	 * includeAll methods.  It creates 3 files:
	 * <ul>
	 * <li>2 groovy files that should be included with an includeAll</li>
	 * <li>An xml file that should be excluded from the includeAll</li>
	 * </ul>
	 * @return the full path of the directory where the files were placed.
	 */
	private String createIncludedChangeLogFiles() {
		createFileFrom(INCLUDED_CHANGELOG_DIR, 'first', '.groovy', """
databaseChangeLog {
  preConditions {
    runningAs(username: 'ssaliman')
  }

  changeSet(author: 'ssaliman', id: '${FIRST_INCLUDED_CHANGE_SET}') {
    renameTable(oldTableName: 'prosaic_table_name', newTableName: 'monkey')
  }
}
""")

		createFileFrom(INCLUDED_CHANGELOG_DIR, 'second', '-2.groovy', """
databaseChangeLog {
  changeSet(author: 'ssaliman', id: '${SECOND_INCLUDED_CHANGE_SET}') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(30)')
    }
  }
}
""")

		createFileFrom(INCLUDED_CHANGELOG_DIR, 'third', '-3.xml', """
<databaseChangeLog>
  <changeSet author="ssaliman" id="included-change-set-3">
    <addColumn tableName="monkey">
      <column name="gender" type="varchar(1)"/>
    </addColumn>
  </changeSet>
</databaseChangeLog>
""")

		return INCLUDED_CHANGELOG_DIR.canonicalPath.replaceAll("\\\\", "/")
	}

	private File createFileFrom(directory, suffix, text) {
		createFileFrom(directory, 'liquibase-', suffix, text)
	}

	private File createFileFrom(directory, prefix, suffix, text) {
		def file = File.createTempFile(prefix, suffix, directory)
		file << text
	}

	/**
	 * Check the preconditions on hehalf of the various "includeAll" tests.
     * Most of the "includeAll" tests use the same included changeSets, so this
	 * helper lets us keep our tests a little cleaner.  We should get 3
	 * preconditions<br>
	 * The first is the DBMSPrecondition from the root ChangeLog.<br>
	 * The second is PreconditionContainer wrapping the RunningAsPrecondition
	 * from the first included changelog.<br>
	 * The third is an empty PreconditionContainer from the second included
	 * changelog.
	 * @param preconditions the preconditions from the root changelog
     */
	private def verifyIncludedPreconditions(preconditions) {
		assertNotNull preconditions
		assertEquals 3, preconditions.size()
		assertTrue preconditions[0] instanceof DBMSPrecondition
		assertTrue preconditions[1] instanceof PreconditionContainer
		def nested = preconditions[1].nestedPreconditions
		assertTrue nested[0] instanceof RunningAsPrecondition
		assertTrue preconditions[2] instanceof PreconditionContainer
		nested = preconditions[2].nestedPreconditions
		assertEquals 0, nested.size()

	}

	/**
	 * Helper method to determine if a given path represents an absolute path.
	 * If the given path is not an absolute path for the platform, this method
	 * will fail the test.
	 * @param path the path to check
	 */
	private def assertAbsolutePath(path) {
		if ( !new File(path).isAbsolute() ) {
			fail "'${path}' is not an absolute path"
		}

	}
}

