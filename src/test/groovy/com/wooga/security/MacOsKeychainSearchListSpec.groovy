/*
 * Copyright 2018-2020 Wooga GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wooga.security

import com.wooga.security.command.ListKeychains
import org.junit.Rule
import org.junit.contrib.java.lang.system.EnvironmentVariables
import org.junit.contrib.java.lang.system.ProvideSystemProperty
import spock.lang.*
import spock.util.environment.RestoreSystemProperties

@Requires({ os.macOs && env['ATLAS_BUILD_UNITY_IOS_EXECUTE_KEYCHAIN_SPEC'] == 'YES' })
@RestoreSystemProperties
class MacOsKeychainSearchListSpec extends Specification {

    @Shared
    File newHome = File.createTempDir("user", "home")

    @Rule
    public ProvideSystemProperty systemProperties = new ProvideSystemProperty("user.home", newHome.path)

    @Shared
    MacOsKeychainSearchList subject = new MacOsKeychainSearchList(Domain.user)

    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables()

    def setup() {
        newHome.mkdirs()
        MacOsKeychainSearchList.resetKeychains()
    }

    def cleanup() {
        MacOsKeychainSearchList.resetKeychains()
    }

    def cleanupSpec() {
        MacOsKeychainSearchList.resetKeychains()
    }

    File createTestKeychainFromPath(String path) {
        if (path.startsWith("~" + File.separator)) {
            path = System.getProperty("user.home") + path.substring(1)
        }
        def f = new File(path).canonicalFile
        createTestKeychain(f.name, f.parentFile)
    }

    File createTestKeychain(String fileName, File baseDir = File.createTempDir()) {
        fileName = fileName.endsWith(".keychain") ? fileName : "${fileName}.keychain"
        def keychain = new File(baseDir, fileName)
        baseDir.mkdirs()
        keychain << "A test keychain file mock"
        keychain
    }

    def "populated list has size != 0"() {
        given: "a populated list"
        subject.add(createTestKeychain("test1"))
        subject.add(createTestKeychain("test2"))

        expect:
        subject.size() != 0
        !subject.isEmpty()
    }

    @Unroll("add/addAll operation fails with #expectedExeption when #reason")
    def "add operation fails with exception when property is invalid"() {
        when:
        subject.add(value)

        then:
        def e = thrown(expectedExeption)
        e.message.matches(messagePattern)

        where:
        //Spock2 gets confused when column starts with a closure, so adding empty column to avoid that (https://github.com/spockframework/spock/issues/1269)
        _ | operation                       | property   | value                  | expectedExeption               | message
        _ | { -> subject.add(value) }       | "keychain" | new File("/some/file") | IllegalArgumentException.class | "does not exist"
        _ | { -> subject.add(value) }       | "keychain" | File.createTempDir()   | IllegalArgumentException.class | "is not a file"
        _ | { -> subject.addAll([value]) }  | "keychain" | new File("/some/file") | IllegalArgumentException.class | "does not exist"
        _ | { -> subject.addAll([value]) }  | "keychain" | File.createTempDir()   | IllegalArgumentException.class | "is not a file"

        reason = "provided ${property} ${message}"
        messagePattern = /provided ${property}.*${message}/
    }

    def "adds a single keychain to the lookup list"() {
        given: "a empty lookup list"
        def initialSize = subject.size()
        assert subject.size() != 0

        when:
        def k1 = createTestKeychain("test")
        def result = subject.add(k1)

        then:
        result
        subject.size() == initialSize + 1
        subject.contains(k1)

        when:
        def k2 = createTestKeychain("test2")
        result = subject.add(k2)

        then:
        result
        subject.size() == initialSize + 2
        subject.contains(k1)
        subject.contains(k2)
    }

    def "adds multiple keychains to the lookup list"() {
        given: "a empty lookup list"
        def initialSize = subject.size()
        assert subject.size() != 0

        when:
        def k1 = createTestKeychain("test.keychain")
        def k2 = createTestKeychain("test2.keychain")
        def k3 = createTestKeychain("test3.keychain")
        def k4 = createTestKeychain("test4.keychain")
        def result = subject.addAll([k1, k2])

        then:
        result
        subject.size() == initialSize + 2
        subject.contains(k1)
        subject.contains(k2)

        when:
        result = subject.addAll([k3, k4])

        then:
        result
        subject.size() == initialSize + 4
        subject.contains(k1)
        subject.contains(k2)
        subject.contains(k3)
        subject.contains(k4)
    }

    @Unroll
    def "doesn't add duplicate entries when #message"() {
        given: "lookup list with one entry"
        def initialSize = subject.size()
        def keychain = createTestKeychain("test.keychain", new File(newHome, "path/to"))
        subject.add(keychain)
        assert subject.size() == initialSize + 1

        when:
        def result = subject.add(new File(fileToAdd))

        then:
        !result
        subject.size() == initialSize + 1

        where:
        fileToAdd                                       | message
        "~/path/to/test.keychain"                       | "path is equal"
        "~/path/../path/to/test.keychain"               | "resolved path is equal"
        "${newHome.path}/path/../path/to/test.keychain" | "expanded ~/ path is equal"
    }

    @Unroll
    def "#method throws #error when #message"() {
        when:
        subject.invokeMethod(method, testObject)

        then:
        thrown(error)

        where:
        method        | testObject | error                     | message
        "contains"    | null       | NullPointerException      | "object is null"
        "indexOf"     | null       | NullPointerException      | "object is null"
        "lastIndexOf" | null       | NullPointerException      | "object is null"
        "contains"    | "test"     | ClassCastException        | "object is not a java.io.File"
        "remove"      | "test"     | ClassCastException        | "object is not a java.io.File"
        "indexOf"     | "test"     | ClassCastException        | "object is not a java.io.File"
        "lastIndexOf" | "test"     | ClassCastException        | "object is not a java.io.File"
        "add"         | "test"     | ClassCastException        | "object is not a java.io.File"
        "get"         | 22         | IndexOutOfBoundsException | "index is out of bounds"
    }

    def "doesn't read keys with non-existent files"() {
        given: "a keychain with a qnon-existing key file"
        def nonExistentKeychain = MacOsKeychainSearchList.canonical(createTestKeychain("keychain_to_be_deleted"))
        assert subject.add(nonExistentKeychain)
        assert new ListKeychains().withDomain(Domain.user).execute().contains(nonExistentKeychain)

        when: "deleting keychain key file"
        nonExistentKeychain.delete()

        then: "keychain with non-existing file is not there in all read operations"
        !nonExistentKeychain.exists()
        !subject.contains(nonExistentKeychain)
        !subject.containsAll([nonExistentKeychain])
        !subject.iterator().find { it == nonExistentKeychain }
        !subject.toArray().contains(nonExistentKeychain)
        !subject.toArray(new File[0]).contains(nonExistentKeychain)
        !(subject.get(subject.size() - 1) == nonExistentKeychain)
        !(subject.indexOf(nonExistentKeychain) > -1)
        !(subject.lastIndexOf(nonExistentKeychain) > -1)
        !(subject.listIterator().find { it == nonExistentKeychain })
        !(subject.listIterator(0).find { it == nonExistentKeychain })
        !subject.subList(0, subject.size()).contains(nonExistentKeychain)
    }

    @Unroll
    def "#operationName doesn't remove any key associated with a non-existent file"() {
        given:
        def nonExistentKeychain = MacOsKeychainSearchList.canonical(createTestKeychain("keychain_to_be_deleted"))
        assert subject.add(nonExistentKeychain)
        assert new ListKeychains().withDomain(Domain.user).execute().contains(nonExistentKeychain)
        assert nonExistentKeychain.delete()

        when:
        operation.call()
        then:
        def allKeychains = new ListKeychains().withDomain(Domain.user).execute()
        allKeychains.contains(nonExistentKeychain)

        where:
        operation                                             | operationName
        subject.&add.curry(createTestKeychain("any"))         | "add"
        subject.&addAll.curry([createTestKeychain("any")])    | "addAll"
        subject.&remove.curry(createTestKeychain("any"))      | "remove"
        subject.&removeAll.curry([createTestKeychain("any")]) | "removeAll"
    }


    // need to unroll these cases by hand because `invokeMethod` calls them with:
    // - non null default buildArguments
    // or
    // - doesn't know which overload to call

    def "retainAll throws UnsupportedOperationException"() {
        when:
        subject.retainAll(null)

        then:
        thrown(UnsupportedOperationException)
    }

    def "set(int,File) throws UnsupportedOperationException"() {
        when:
        subject.set(0, new File('some/file'))

        then:
        thrown(UnsupportedOperationException)
    }

    def "add(int,File) throws UnsupportedOperationException"() {
        when:
        subject.add(0, new File('some/file'))

        then:
        thrown(UnsupportedOperationException)
    }

    def "remove(int) throws UnsupportedOperationException"() {
        when:
        subject.remove(0)

        then:
        thrown(UnsupportedOperationException)
    }

    def "addAll(int, Collection<? extends File>) throws UnsupportedOperationException"() {
        when:
        subject.addAll(0, [new File('some/file')])

        then:
        thrown(UnsupportedOperationException)
    }


    def "remove throws NullPointerException when object is null"() {
        when:
        subject.remove(null)

        then:
        thrown(NullPointerException)
    }

    def "add throws NullPointerException when object is null"() {
        when:
        subject.add(null)

        then:
        thrown(NullPointerException)
    }

    def "addAll throws NullPointerException when object is null"() {
        when:
        subject.addAll(null as Collection)

        then:
        thrown(NullPointerException)
    }

    def "addAll throws NullPointerException when one or more objects in list are null"() {
        when:
        subject.addAll([createTestKeychain("test.keychain"), null])

        then:
        thrown(NullPointerException)
    }

    def "removeAll throws NullPointerException when object is null"() {
        when:
        subject.removeAll(null)

        then:
        thrown(NullPointerException)
    }

    def "containsAll throws NullPointerException when object is null"() {
        when:
        subject.containsAll(null)

        then:
        thrown(NullPointerException)
    }

    def "containsAll throws ClassCastException when one or more objects in list are not of type java.io.File"() {
        when:
        subject.containsAll([new File('some/file'), "a String"])

        then:
        thrown(ClassCastException)
    }

    def "toArray throws NullPointerException when object is null"() {
        when:
        subject.toArray(null)

        then:
        thrown(NullPointerException)
    }

    def "toArray throws ArrayStoreException when object is not a java.io.File[] array"() {
        given: "lookup list with multiple entries"
        subject.add(new File("~/path/to/test.keychain"))

        when:
        subject.toArray(new String[0])

        then:
        thrown(ArrayStoreException)
    }

    @Unroll
    def "contains checks if resolved path are equal when #message"() {
        given: "lookup list with one entry"
        def initialSize = subject.size()
        subject.add(new File("~/path/to/test.keychain"))
        assert subject.size() == initialSize + 1

        expect:
        subject.contains(new File(fileToCheck))

        where:
        fileToCheck                                     | message
        //"~/path/to/test.keychain"                       | "path is equal"
        //"~/path/../path/to/test.keychain"               | "resolved path is equal"
        "${newHome.path}/path/../path/to/test.keychain" | "expanded ~/ path is equal"
    }

    @Unroll
    def "containsAll checks if all keychains provided are in the list"() {
        given: "lookup list with multiple entries"
        def initialSize = subject.size()
        subject.add(createTestKeychainFromPath("~/path/to/test.keychain"))
        subject.add(createTestKeychainFromPath("~/path/to/test2.keychain"))
        subject.add(createTestKeychainFromPath("~/path/to/test3.keychain"))
        assert subject.size() == initialSize + 3

        expect:
        subject.containsAll(check) == expectedValue

        where:
        check                                                                       || expectedValue
        [new File("~/path/to/test.keychain")]                                       || true
        [new File("~/path/to/test.keychain"), new File("~/path/to/test2.keychain")] || true
        [new File("~/path/to/test.keychain"), new File("~/path/to/test4.keychain")] || false
        [new File("~/path/to/test4.keychain")]                                      || false
    }

    @Unroll
    def "clear resets keychain"() {
        given: "lookup list with multiple entries"
        def initialSize = subject.size()
        subject.add(createTestKeychainFromPath("~/path/to/test.keychain"))
        subject.add(createTestKeychainFromPath("~/path/to/test2.keychain"))
        subject.add(createTestKeychainFromPath("~/path/to/test3.keychain"))
        assert subject.size() == initialSize + 3

        when:
        subject.clear()

        then:
        !subject.isEmpty()
        subject.size() == initialSize
        !subject.contains(MacOsKeychainSearchList.canonical(new File("~/path/to/test.keychain")))
        !subject.contains(MacOsKeychainSearchList.canonical(new File("~/path/to/test2.keychain")))
        !subject.contains(MacOsKeychainSearchList.canonical(new File("~/path/to/test3.keychain")))
    }

    @Unroll
    def "removes items from the list when #message"() {
        given: "lookup list with multiple entries"
        def initialSize = subject.size()
        subject.add(createTestKeychainFromPath("~/path/to/test.keychain"))
        subject.add(createTestKeychainFromPath("~/path/to/test2.keychain"))
        subject.add(createTestKeychainFromPath("~/path/to/test3.keychain"))
        assert subject.size() == initialSize + 3

        when:
        def result = subject.remove(new File(fileToRemove))

        then:
        result
        subject.size() == initialSize + 2
        !subject.contains(new File(fileToRemove))

        where:
        fileToRemove                                    | message
        "~/path/to/test.keychain"                       | "path is equal"
        "~/path/../path/to/test.keychain"               | "resolved path is equal"
        "${newHome.path}/path/../path/to/test.keychain" | "expanded ~/ path is equal"
    }

    @Unroll
    def "removes multiple items from the list"() {
        given: "lookup list with multiple entries"
        def initialSize = subject.size()
        subject.add(createTestKeychainFromPath("~/path/to/test.keychain"))
        subject.add(createTestKeychainFromPath("~/path/to/test2.keychain"))
        subject.add(createTestKeychainFromPath("~/path/to/test3.keychain"))
        assert subject.size() == initialSize + 3

        when:
        def result = subject.removeAll(filesToRemove)

        then:
        result == hasChanges
        subject.size() == initialSize + expectedSize
        !subject.containsAll(filesToRemove)

        where:
        filesToRemove                                                               || expectedSize | hasChanges
        [new File("~/path/to/test.keychain")]                                       || 2            | true
        [new File("~/path/to/test.keychain"), new File("~/path/to/test2.keychain")] || 1            | true
        [new File("~/path/to/test.keychain"), new File("~/path/to/test4.keychain")] || 2            | true
        [new File("~/path/to/test4.keychain")]                                      || 3            | false
    }

    @Unroll
    def "creates #type over items"() {
        given: "lookup list with multiple entries"
        def initialSize = subject.size()
        subject.add(createTestKeychainFromPath("~/path/to/test.keychain"))
        subject.add(createTestKeychainFromPath("~/path/to/test2.keychain"))
        subject.add(createTestKeychainFromPath("~/path/to/test3.keychain"))
        assert subject.size() == initialSize + 3

        and: "a check counter"
        def checkList = []

        when:
        def iter = subject.invokeMethod(method, null)
        while (iter.hasNext()) {
            checkList.add(iter.next())
        }

        then:
        checkList.size() == subject.size()
        subject.containsAll(checkList)

        where:
        type            | method
        "iterator"      | "iterator"
        "list iterator" | "listIterator"
    }

    @Unroll
    def "list iterator can move in both directions"() {
        given: "lookup list with multiple entries"
        def initialSize = subject.size()
        subject.add(createTestKeychainFromPath("~/path/to/test.keychain"))
        subject.add(createTestKeychainFromPath("~/path/to/test2.keychain"))
        subject.add(createTestKeychainFromPath("~/path/to/test3.keychain"))
        assert subject.size() == initialSize + 3

        and: "a check counter"
        def checkList = []

        when:
        def iter = subject.listIterator(3)
        while (iter.hasPrevious()) {
            iter.previousIndex()
            checkList.add(iter.previous())
        }

        then:
        checkList.size() + initialSize == subject.size()
        subject.containsAll(checkList)

        when:
        checkList.clear()
        iter = subject.listIterator(0)
        while (iter.hasNext()) {
            iter.nextIndex()
            checkList.add(iter.next())
        }

        then:
        checkList.size() == subject.size()
        subject.containsAll(checkList)
    }

    def "list iterator doesn't support set(File)"() {
        given: "lookup list with multiple entries"
        def initialSize = subject.size()
        subject.add(createTestKeychainFromPath("~/path/to/test.keychain"))
        subject.add(createTestKeychainFromPath("~/path/to/test2.keychain"))
        subject.add(createTestKeychainFromPath("~/path/to/test3.keychain"))
        assert subject.size() == initialSize + 3

        when:
        def iter = subject.listIterator()
        iter.next()
        iter.set(new File("some/file"))

        then:
        thrown(UnsupportedOperationException)
    }

    def "list iterator doesn't support add(File)"() {
        given: "lookup list with multiple entries"
        def initialSize = subject.size()
        subject.add(createTestKeychainFromPath("~/path/to/test.keychain"))
        subject.add(createTestKeychainFromPath("~/path/to/test2.keychain"))
        subject.add(createTestKeychainFromPath("~/path/to/test3.keychain"))
        assert subject.size() == initialSize + 3

        when:
        def iter = subject.listIterator()
        iter.next()
        iter.add(new File("some/file"))

        then:
        thrown(UnsupportedOperationException)
    }

    @Unroll
    def "#type can modify lookup list"() {
        given: "lookup list with multiple entries"
        def initialSize = subject.size()
        subject.add(createTestKeychainFromPath("~/path/to/test.keychain"))
        subject.add(createTestKeychainFromPath("~/path/to/test2.keychain"))
        subject.add(createTestKeychainFromPath("~/path/to/test3.keychain"))
        assert subject.size() == initialSize + 3

        when:
        Iterator iter = subject.invokeMethod(method, null)
        while (iter.hasNext()) {
            if (iter.next() == MacOsKeychainSearchList.canonical(new File("~/path/to/test2.keychain"))) {
                iter.remove()
            }
        }

        then:
        subject.size() == initialSize + 2
        !subject.contains(new File("~/path/to/test2.keychain"))

        where:
        type            | method
        "iterator"      | "iterator"
        "list iterator" | "listIterator"
    }

    def "creates Object[] array copy of items"() {
        given: "lookup list with multiple entries"
        def initialSize = subject.size()
        subject.add(createTestKeychainFromPath("~/path/to/test.keychain"))
        subject.add(createTestKeychainFromPath("~/path/to/test2.keychain"))
        subject.add(createTestKeychainFromPath("~/path/to/test3.keychain"))
        assert subject.size() == initialSize + 3

        when:
        def arr = subject.toArray()

        then:
        arr.length == subject.size()
        subject.containsAll(arr)

        when:
        arr = subject.toArray()
        subject.remove(new File("~/path/to/test2.keychain"))

        then:
        arr.length != subject.size()
        !subject.containsAll(arr)
    }

    @Unroll
    def "creates File[] array copy of items with #testArr"() {
        given: "lookup list with multiple entries"
        def initialSize = subject.size()
        subject.add(createTestKeychainFromPath("~/path/to/test.keychain"))
        subject.add(createTestKeychainFromPath("~/path/to/test2.keychain"))
        subject.add(createTestKeychainFromPath("~/path/to/test3.keychain"))
        assert subject.size() == initialSize + 3

        and:
        def expectSameSize = testArr.length <= subject.size()

        when:
        def arr = subject.toArray(testArr)

        then:
        (arr.length == subject.size()) == expectSameSize
        subject.containsAll(arr.findAll { it != null })

        when:
        arr = subject.toArray()
        subject.remove(new File("~/path/to/test2.keychain"))

        then:
        arr.length != subject.size()
        !subject.containsAll(arr.findAll { it != null })

        where:
        testArr << [new File[0], new File[3], new File[5]]
    }

    def "retrieves item by index"() {
        given: "lookup list with multiple entries"
        def initialSize = subject.size()
        subject.add(createTestKeychainFromPath("~/path/to/test.keychain"))
        subject.add(createTestKeychainFromPath("~/path/to/test2.keychain"))
        subject.add(createTestKeychainFromPath("~/path/to/test3.keychain"))
        assert subject.size() == initialSize + 3

        expect:
        subject[initialSize + 1] == MacOsKeychainSearchList.canonical(new File("~/path/to/test2.keychain"))
    }

    @Unroll
    def "#method returns #message"() {
        given: "lookup list with multiple entries"
        def initialSize = subject.size()
        subject.add(createTestKeychainFromPath("~/path/to/test.keychain"))
        subject.add(createTestKeychainFromPath("~/path/to/test2.keychain"))
        subject.add(createTestKeychainFromPath("~/path/to/test3.keychain"))
        assert subject.size() == initialSize + 3

        and: "adjusted expected index"
        expectedIndex = (expectedIndex >= 0) ? expectedIndex + initialSize : expectedIndex

        expect:
        subject.invokeMethod(method, item) == expectedIndex

        where:
        method        | item                                 | message                                | expectedIndex
        "indexOf"     | new File("~/path/to/test2.keychain") | "index when item is contained in list" | 1
        "indexOf"     | new File("~/path/to/test4.keychain") | "-1 when item can not be found"        | -1
        "lastIndexOf" | new File("~/path/to/test2.keychain") | "index when item is contained in list" | 1
        "lastIndexOf" | new File("~/path/to/test4.keychain") | "-1 when item can not be found"        | -1
    }

    def "creates a faulty sublist"() {
        given: "lookup list with multiple entries"
        def initialSize = subject.size()
        subject.add(createTestKeychainFromPath("~/path/to/test.keychain"))
        subject.add(createTestKeychainFromPath("~/path/to/test2.keychain"))
        subject.add(createTestKeychainFromPath("~/path/to/test3.keychain"))
        assert subject.size() == initialSize + 3

        expect:
        subject.subList(1, 2).size() == 1
    }

    @Ignore
    def "reset "() {
        given: "lookup list with multiple entries"
        def initialSize = subject.size()
        subject.add(createTestKeychainFromPath("~/path/to/test.keychain"))
        subject.add(createTestKeychainFromPath("~/path/to/test2.keychain"))
        subject.add(createTestKeychainFromPath("~/path/to/test3.keychain"))
        assert subject.size() == initialSize + 3

        and: "a custom reset list"
        def originalDefaultKeychains = System.getenv("ATLAS_BUILD_UNITY_IOS_DEFAULT_KEYCHAINS")
        environmentVariables.set("ATLAS_BUILD_UNITY_IOS_DEFAULT_KEYCHAINS", expectedKeychains.join(File.pathSeparator))

        when:
        subject.reset()

        then:
        subject.size() == expectedKeychains.size()
        expectedKeychains.every { subject.contains(new File(it)) }

        cleanup:
        environmentVariables.set("ATLAS_BUILD_UNITY_IOS_DEFAULT_KEYCHAINS", originalDefaultKeychains)
        MacOsKeychainSearchList.resetKeychains()

        where:
        expectedKeychains = ["~/path/to/test.keychain", "~/path/to/test3.keychain"]
    }

}
