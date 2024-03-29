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

package com.wooga.security.command

import com.wooga.security.Domain
import spock.lang.Requires
import spock.lang.Unroll

@Requires({os.macOs})
class ListKeychainsSpec extends SecurityCommandSpec<ListKeychains> {
    ListKeychains command = new ListKeychains()

    def "lists keychain lookup list"() {
        when:
        def result = command.execute()

        then:
        result != null
        !result.empty
    }

    @Unroll("property #property sets commandline flag #commandlineFlag")
    def "property sets commandline"() {
        given: "set property"
        command.invokeMethod(method, value)

        when:
        def arguments = command.getArguments().join(" ")
        then:
        arguments.contains(expectedArguments)

        where:
        property                | commandlineFlag | value
        "setKeychainSearchList" | "-s"            | true
        "domain"                | "-d"            | Domain.user
        expectedArguments = Boolean.isInstance(value) ? "${commandlineFlag}" : "${commandlineFlag} ${value.toString()}"
        method = Boolean.isInstance(value) ? property : "with${property.capitalize()}"
    }
}
