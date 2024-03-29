/*
 * Copyright 2021 Wooga GmbH
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

trait FindPassword<T extends SecurityCommand> extends MultiKeychainCommand<T> {

    Boolean printPassword

    T printPassword(Boolean value = true) {
        this.printPassword = value
        this as T
    }

    Boolean printPasswordOnly

    T printPasswordOnly(Boolean value = true) {
        this.printPasswordOnly = value
        this as T
    }

    List<String> getFindPasswordArguments() {
        def arguments = []
        if (printPassword) {
            arguments << "-g"
        }

        if (printPasswordOnly) {
            arguments << "-w"
        }
        if(!keychains.empty) {
            SecurityCommand.validateKeychainsProperty(keychains)
            arguments.addAll(getMultiKeychainsArgument())
        }
        arguments
    }
}
