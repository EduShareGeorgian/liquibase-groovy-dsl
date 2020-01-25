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
import liquibase.database.ObjectQuotingStrategy
import liquibase.exception.ChangeLogParseException
import liquibase.parser.ChangeLogParser
import liquibase.parser.ChangeLogParserFactory
import liquibase.parser.ext.GroovyLiquibaseChangeLogParser
import liquibase.precondition.Precondition
import liquibase.precondition.core.DBMSPrecondition
import liquibase.precondition.core.PreconditionContainer
import liquibase.resource.FileSystemResourceAccessor
import org.junit.After
import org.junit.Before
import org.junit.Test

import java.lang.reflect.Field

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertNull
import static org.junit.Assert.assertTrue

/**
 * One of three test classes for the {@link DatabaseChangeLogDelegate}.  The
 * number of tests for {@link DatabaseChangeLogDelegate} were getting unwieldy,
 * so they were split up.  this class deals with all the non-include related
 * tests.
 *
 * @author Steven C. Saliman
 */
class DatabaseChangeLogDelegateTests {
	// Let's define some paths and directories.  These should all be relative.
	static final ROOT_CHANGELOG_PATH = "src/test/changelog"
	static final TMP_CHANGELOG_PATH = ROOT_CHANGELOG_PATH + "/tmp"
	static final TMP_CHANGELOG_DIR = new File(TMP_CHANGELOG_PATH)
	static final EMPTY_CHANGELOG = "${ROOT_CHANGELOG_PATH}/empty-changelog.groovy"
	static final SIMPLE_CHANGELOG = "${ROOT_CHANGELOG_PATH}/simple-changelog.groovy"

	def resourceAccessor
	ChangeLogParserFactory parserFactory


	@Before
	void registerParser() {
		resourceAccessor = new FileSystemResourceAccessor()
		parserFactory = ChangeLogParserFactory.instance
		ChangeLogParserFactory.getInstance().register(new GroovyLiquibaseChangeLogParser())
		// make sure we start with clean temporary directories before each test
		TMP_CHANGELOG_DIR.deleteDir()
		TMP_CHANGELOG_DIR.mkdirs()
	}

	/**
	 * Attempt to clean up included files and directories.  We do this every time
	 * to make sure we start clean each time.  The includeAll test depends on it.
	 */
	@After
	void cleanUp() {
		TMP_CHANGELOG_DIR.deleteDir()
	}

	@Test
	void parseEmptyChangelog() {
		def parser = parserFactory.getParser(EMPTY_CHANGELOG, resourceAccessor)

		assertNotNull "Groovy changelog parser was not found", parser

		def changeLog = parser.parse(EMPTY_CHANGELOG, null, resourceAccessor)
		assertNotNull "Parsed DatabaseChangeLog was null", changeLog
		assertTrue "Parser result was not a DatabaseChangeLog", changeLog instanceof DatabaseChangeLog
	}


	@Test
	void parseSimpleChangelog() {
		def parser = parserFactory.getParser(SIMPLE_CHANGELOG, resourceAccessor)

		assertNotNull "Groovy changelog parser was not found", parser

		def changeLog = parser.parse(SIMPLE_CHANGELOG, null, resourceAccessor)
		assertNotNull "Parsed DatabaseChangeLog was null", changeLog
		assertTrue "Parser result was not a DatabaseChangeLog", changeLog instanceof DatabaseChangeLog
		assertEquals '.', changeLog.logicalFilePath
		assertEquals "myContext", changeLog.contexts.toString()

		def changeSets = changeLog.changeSets
		assertEquals 1, changeSets.size()
		def changeSet = changeSets[0]
		assertNotNull "ChangeSet was null", changeSet
		assertEquals 'stevesaliman', changeSet.author
		assertEquals 'change-set-001', changeSet.id
	}

	@Test(expected=ChangeLogParseException)
	void parsingEmptyDatabaseChangeLogFails() {
		def changeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog()
""")
		def parser = parserFactory.getParser(changeLogFile.absolutePath, resourceAccessor)
		parser.parse(changeLogFile.absolutePath, null, resourceAccessor)
	}


	@Test
	void parsingDatabaseChangeLogAsProperty() {
		File changeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
    databaseChangeLog = {
    }
    """)
		ChangeLogParser parser = parserFactory.getParser(changeLogFile.absolutePath, resourceAccessor)
		DatabaseChangeLog changeLog = parser.parse(changeLogFile.absolutePath, null, resourceAccessor)

		assertNotNull "Parsed DatabaseChangeLog was null", changeLog
	}


	@Test
	void preconditionParameters() {
		def closure = {
			preConditions(onFail: 'WARN', onError: 'MARK_RAN', onUpdateSQL: 'TEST', onFailMessage: 'fail-message!!!1!!1one!', onErrorMessage: 'error-message') {

			}
		}

		def databaseChangeLog = new DatabaseChangeLog('changelog.xml')
	    databaseChangeLog.changeLogParameters = new ChangeLogParameters()
		def delegate = new DatabaseChangeLogDelegate(databaseChangeLog)
		closure.delegate = delegate
		closure.call()

		def preconditions = databaseChangeLog.preconditions
		assertNotNull preconditions
		assertTrue preconditions instanceof PreconditionContainer
		assertEquals PreconditionContainer.FailOption.WARN, preconditions.onFail
		assertEquals PreconditionContainer.ErrorOption.MARK_RAN, preconditions.onError
		assertEquals PreconditionContainer.OnSqlOutputOption.TEST, preconditions.onSqlOutput
		assertEquals 'fail-message!!!1!!1one!', preconditions.onFailMessage
		assertEquals 'error-message', preconditions.onErrorMessage
	}

	/**
	 * Test creating a changeSet with no attributes. This verifies that we use
	 * expected default values when a value is not provided.
	 */
	@Test
	void changeSetEmpty() {
		def changeLog = buildChangeLog {
			changeSet([:]) {}
		}
		assertNotNull changeLog.changeSets
		assertEquals 1, changeLog.changeSets.size()
		assertNull changeLog.changeSets[0].id
		assertNull changeLog.changeSets[0].author
		assertFalse changeLog.changeSets[0].alwaysRun
		// the property doesn't match xml or docs.
		assertFalse changeLog.changeSets[0].runOnChange
		assertEquals ROOT_CHANGELOG_PATH, changeLog.changeSets[0].filePath
		assertEquals 0, changeLog.changeSets[0].contexts.contexts.size()
		assertNull changeLog.changeSets[0].labels
		assertNull changeLog.changeSets[0].dbmsSet
		assertTrue changeLog.changeSets[0].runInTransaction
		assertNull changeLog.changeSets[0].failOnError
		assertEquals "HALT", changeLog.changeSets[0].onValidationFail.toString()
		assertNull changeLog.changeSets[0].created
		assertNull changeLog.changeSets[0].runOrder
		assertFalse changeLog.changeSets[0].ignore
	}

	/**
	 * Test creating a changeSet with all supported attributes.  We support
	 * filePath and logicalFilepath.  This test uses filePath
	 */
	@Test
	void changeSetFull() {
		def changeLog = buildChangeLog {
			changeSet(id: 'monkey-change',
					  author: 'stevesaliman',
					  dbms: 'mysql',
					  runAlways: true,
					  runOnChange: true,
					  context: 'testing',
					  labels: 'test_label',
					  runInTransaction: false,
					  failOnError: true,
					  onValidationFail: "MARK_RAN",
					  objectQuotingStrategy: "QUOTE_ONLY_RESERVED_WORDS",
			          created: 'test_created',
			          runOrder: 'last',
			          ignore: true,
			          filePath: 'file_path') {
			  dropTable(tableName: 'monkey')
			}
		}

		assertNotNull changeLog.changeSets
		assertEquals 1, changeLog.changeSets.size()
		assertEquals 'monkey-change', changeLog.changeSets[0].id
		assertEquals 'stevesaliman', changeLog.changeSets[0].author
		assertTrue changeLog.changeSets[0].alwaysRun // the property doesn't match xml or docs.
		assertTrue changeLog.changeSets[0].runOnChange
		assertEquals 'file_path', changeLog.changeSets[0].filePath
		assertEquals 'testing', changeLog.changeSets[0].contexts.contexts.toArray()[0]
		assertEquals 'test_label', changeLog.changeSets[0].labels.toString()
		assertEquals 'mysql', changeLog.changeSets[0].dbmsSet.toArray()[0]
		assertFalse changeLog.changeSets[0].runInTransaction
		assertTrue changeLog.changeSets[0].failOnError
		assertEquals "MARK_RAN", changeLog.changeSets[0].onValidationFail.toString()
		assertEquals ObjectQuotingStrategy.QUOTE_ONLY_RESERVED_WORDS, changeLog.changeSets[0].objectQuotingStrategy
		assertEquals 'test_created', changeLog.changeSets[0].created
		assertEquals 'last', changeLog.changeSets[0].runOrder
		assertTrue changeLog.changeSets[0].ignore
	}

	/**
	 * Test creating a changeSet with all supported attributes.  We support
	 * filePath and logicalFilepath.  This test uses logicalFilePath
	 */
	@Test
	void changeSetFullLogicalFilePath() {
		def changeLog = buildChangeLog {
			changeSet(id: 'monkey-change',
					  author: 'stevesaliman',
					  dbms: 'mysql',
					  runAlways: true,
					  runOnChange: true,
					  context: 'testing',
					  labels: 'test_label',
					  runInTransaction: false,
					  failOnError: true,
					  onValidationFail: "MARK_RAN",
					  objectQuotingStrategy: "QUOTE_ONLY_RESERVED_WORDS",
			          created: 'test_created',
			          runOrder: 'last',
			          ignore: true,
			          logicalFilePath: 'file_path') {
			  dropTable(tableName: 'monkey')
			}
		}

		assertNotNull changeLog.changeSets
		assertEquals 1, changeLog.changeSets.size()
		assertEquals 'monkey-change', changeLog.changeSets[0].id
		assertEquals 'stevesaliman', changeLog.changeSets[0].author
		assertTrue changeLog.changeSets[0].alwaysRun // the property doesn't match xml or docs.
		assertTrue changeLog.changeSets[0].runOnChange
		assertEquals 'file_path', changeLog.changeSets[0].filePath
		assertEquals 'testing', changeLog.changeSets[0].contexts.contexts.toArray()[0]
		assertEquals 'test_label', changeLog.changeSets[0].labels.toString()
		assertEquals 'mysql', changeLog.changeSets[0].dbmsSet.toArray()[0]
		assertFalse changeLog.changeSets[0].runInTransaction
		assertTrue changeLog.changeSets[0].failOnError
		assertEquals "MARK_RAN", changeLog.changeSets[0].onValidationFail.toString()
		assertEquals ObjectQuotingStrategy.QUOTE_ONLY_RESERVED_WORDS, changeLog.changeSets[0].objectQuotingStrategy
		assertEquals 'test_created', changeLog.changeSets[0].created
		assertEquals 'last', changeLog.changeSets[0].runOrder
		assertTrue changeLog.changeSets[0].ignore
	}

	/**
	 * Test creating a changeSet with all supported attributes, and one of them
	 * has an expression to expand.  This test will omit the filePath and
	 * logicalFilePath attributes to make sure we get the correct default.
	 */
	@Test
	void changeSetFullWithProperties() {
		def changeLog = buildChangeLog {
			property(name: 'authName', value: 'stevesaliman')
			changeSet(id: 'monkey-change',
					author: '\${authName}',
					dbms: 'mysql',
					runAlways: true,
					runOnChange: true,
					context: 'testing',
					labels: 'test_label',
					runInTransaction: false,
					failOnError: true,
					onValidationFail: "MARK_RAN",
					objectQuotingStrategy: "QUOTE_ONLY_RESERVED_WORDS",
					created: 'test_created',
					runOrder: 'first',
					ignore: false) {
				dropTable(tableName: 'monkey')
			}
		}

		assertNotNull changeLog.changeSets
		assertEquals 1, changeLog.changeSets.size()
		assertEquals 'monkey-change', changeLog.changeSets[0].id
		assertEquals 'stevesaliman', changeLog.changeSets[0].author
		assertTrue changeLog.changeSets[0].alwaysRun // the property doesn't match xml or docs.
		assertTrue changeLog.changeSets[0].runOnChange
		assertEquals ROOT_CHANGELOG_PATH, changeLog.changeSets[0].filePath
		assertEquals 'testing', changeLog.changeSets[0].contexts.contexts.toArray()[0]
		assertEquals 'test_label', changeLog.changeSets[0].labels.toString()
		assertEquals 'mysql', changeLog.changeSets[0].dbmsSet.toArray()[0]
		assertFalse changeLog.changeSets[0].runInTransaction
		assertTrue changeLog.changeSets[0].failOnError
		assertEquals "MARK_RAN", changeLog.changeSets[0].onValidationFail.toString()
		assertEquals ObjectQuotingStrategy.QUOTE_ONLY_RESERVED_WORDS, changeLog.changeSets[0].objectQuotingStrategy
		assertEquals 'test_created', changeLog.changeSets[0].created
		assertEquals 'first', changeLog.changeSets[0].runOrder
		assertFalse changeLog.changeSets[0].ignore
	}

	/**
	 * Test creating a changeSet with an unsupported attribute.
	 */
	@Test(expected = ChangeLogParseException)
	void changeSetInvalidAttribute() {
		buildChangeLog {
			changeSet(id: 'monkey-change',
					  author: 'stevesaliman',
					  dbms: 'mysql',
					  runAlways: false,
					  runOnChange: true,
					  context: 'testing',
					  labels: 'test_label',
					  runInTransaction: false,
					  failOnError: true,
					  onValidationFail: "MARK_RAN",
			          invalidAttribute: 'invalid') {
				dropTable(tableName: 'monkey')
			}
		}
	}

	/**
	 * Test creating a changeSet with an unsupported Object quoting strategy.
	 */
	@Test(expected = ChangeLogParseException)
	void changeSetInvalidQuotingStrategy() {
		buildChangeLog {
			changeSet(id: 'monkey-change',
					author: 'stevesaliman',
					dbms: 'mysql',
					runAlways: false,
					runOnChange: true,
					context: 'testing',
					labels: 'test_label',
					runInTransaction: false,
					failOnError: true,
					onValidationFail: "MARK_RAN",
					objectQuotingStrategy: "MONKEY_QUOTING") {
				dropTable(tableName: 'monkey')
			}
		}
	}

	/**
	 * Test change log preconditions.  This uses the same delegate as change set
	 * preconditions, so we don't have to do much here, just make sure we can
	 * call the correct thing from a change log and have the change log altered.
	 */
	@Test
	void preconditionsInChangeLog() {
		def changeLog = buildChangeLog {
			preConditions {
				dbms(type: 'mysql')
			}
		}

		assertEquals 0, changeLog.changeSets.size()
		assertNotNull changeLog.preconditions
		assertTrue changeLog.preconditions.nestedPreconditions.every { precondition -> precondition instanceof Precondition }
		assertEquals 1, changeLog.preconditions.nestedPreconditions.size()
		assertTrue changeLog.preconditions.nestedPreconditions[0] instanceof DBMSPrecondition
		assertEquals 'mysql', changeLog.preconditions.nestedPreconditions[0].type

	}

	/**
	 * Try adding a property with an invalid attribute
	 */
	@Test(expected = ChangeLogParseException)
	void propertyInvalidAttribute() {
		buildChangeLog {
			property(propertyName: 'invalid', propertyValue: 'invalid')
		}
	}

	/**
	 * Try creating an empty property.
	 */
	@Test
	void propertyEmpty() {
		def changeLog = buildChangeLog {
			property([:])
		}

		// change log parameters are not exposed through the API, so get them
		// using reflection.  Also, there are
		def changeLogParameters = changeLog.changeLogParameters
		Field f = changeLogParameters.getClass().getDeclaredField("changeLogParameters")
		f.setAccessible(true)
		def properties = f.get(changeLogParameters)
		def property = properties[properties.size()-1] // The last one is ours.
		assertNull property.key
		assertNull property.value
		assertNull property.validDatabases
		assertNull property.validContexts
		assertNull property.labels
	}

	/**
	 * Try creating a property with a name and value only.  Make sure we don't
	 * try to set the database or contexts
	 */
	@Test
	void propertyPartial() {
		def changeLog = buildChangeLog {
			property(name: 'emotion', value: 'angry')
		}

		// change log parameters are not exposed through the API, so get them
		// using reflection.  Also, there are
		def changeLogParameters = changeLog.changeLogParameters
		Field f = changeLogParameters.getClass().getDeclaredField("changeLogParameters")
		f.setAccessible(true)
		def properties = f.get(changeLogParameters)
		def property = properties[properties.size()-1] // The last one is ours.
		assertEquals 'emotion', property.key
		assertEquals 'angry', property.value
		assertNull property.validDatabases
		assertNull property.validContexts
		assertNull property.labels
	}

	/**
	 * Try creating a property with all supported attributes, and a boolean for
	 * the global attribute.
	 */
	@Test
	void propertyFullBooleanGlobal() {
		def changeLog = buildChangeLog {
			property(name: 'emotion', value: 'angry', dbms: 'mysql', labels: 'test_label', context: 'test', 'global': true)
		}

		// change log parameters are not exposed through the API, so get them
		// using reflection.
		def changeLogParameters = changeLog.changeLogParameters
		Field f = changeLogParameters.getClass().getDeclaredField("changeLogParameters")
		f.setAccessible(true)
		def properties = f.get(changeLogParameters)
		def property = properties[properties.size()-1] // The last one is ours.
		assertEquals 'emotion', property.key
		assertEquals 'angry', property.value
		assertEquals 'mysql', property.validDatabases[0]
		assertEquals 'test', property.validContexts.contexts.toArray()[0]
		assertEquals 'test_label', property.labels.toString()
	}

	/**
	 * Try creating a property with all supported attributes and a String for
	 * the global attribute.
	 */
	@Test
	void propertyFullStringGlobal() {
		def changeLog = buildChangeLog {
			property(name: 'emotion', value: 'angry', dbms: 'mysql', labels: 'test_label', context: 'test', 'global': 'true')
		}

		// change log parameters are not exposed through the API, so get them
		// using reflection.
		def changeLogParameters = changeLog.changeLogParameters
		Field f = changeLogParameters.getClass().getDeclaredField("changeLogParameters")
		f.setAccessible(true)
		def properties = f.get(changeLogParameters)
		def property = properties[properties.size()-1] // The last one is ours.
		assertEquals 'emotion', property.key
		assertEquals 'angry', property.value
		assertEquals 'mysql', property.validDatabases[0]
		assertEquals 'test', property.validContexts.contexts.toArray()[0]
		assertEquals 'test_label', property.labels.toString()
	}

	/**
	 * Try including a property from a file that doesn't exist.
	 */
	@Test(expected = ChangeLogParseException)
	void propertyFromInvalidFile() {
		def changeLog = buildChangeLog {
			property(file: "${TMP_CHANGELOG_DIR}/bad.properties")
		}
	}

	/**
	 * Try including a property from a file when we don't hae a dbms or context.
	 */
	@Test
	void propertyFromFilePartial() {
		def propertyFile = createFileFrom(TMP_CHANGELOG_DIR, '.properties', """
emotion=angry
""")
		propertyFile = propertyFile.canonicalPath
		propertyFile = propertyFile.replaceAll("\\\\", "/")

		def changeLog = buildChangeLog {
			property(file: "${propertyFile}")
		}


		// change log parameters are not exposed through the API, so get them
		// using reflection.  Also, there are
		def changeLogParameters = changeLog.changeLogParameters
		Field f = changeLogParameters.getClass().getDeclaredField("changeLogParameters")
		f.setAccessible(true)
		def properties = f.get(changeLogParameters)
		def property = properties[properties.size()-1] // The last one is ours.
		assertEquals 'emotion', property.key
		assertEquals 'angry', property.value
		assertNull property.validDatabases
		assertNull property.validContexts
		assertNull property.labels
	}

	/**
	 * Try including a property from a file when we do have a context and dbms..
	 */
	@Test
	void propertyFromFileFull() {
		def propertyFile = createFileFrom(TMP_CHANGELOG_DIR, '.properties', """
emotion=angry
""")
		propertyFile = propertyFile.canonicalPath
		propertyFile = propertyFile.replaceAll("\\\\", "/")

		def changeLog = buildChangeLog {
			property(file: "${propertyFile}", dbms: 'mysql', context: 'test', labels: 'test_label')
		}

		// change log parameters are not exposed through the API, so get them
		// using reflection.  Also, there are
		def changeLogParameters = changeLog.changeLogParameters
		Field f = changeLogParameters.getClass().getDeclaredField("changeLogParameters")
		f.setAccessible(true)
		def properties = f.get(changeLogParameters)
		def property = properties[properties.size()-1] // The last one is ours.
		assertEquals 'emotion', property.key
		assertEquals 'angry', property.value
		assertEquals 'mysql', property.validDatabases[0]
		assertEquals 'test', property.validContexts.contexts.toArray()[0]
		assertEquals 'test_label', property.labels.toString()
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


	private File createFileFrom(directory, suffix, text) {
		createFileFrom(directory, 'liquibase-', suffix, text)
	}

	private File createFileFrom(directory, prefix, suffix, text) {
		def file = File.createTempFile(prefix, suffix, directory)
		file << text
	}

}

