/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.globo.globodns.cloudstack.commands;

import com.cloud.agent.api.Command;

public class UpdateRecordCommand extends Command {
	
	private Long domainId;
	
	private Long recordId;
	
	private String name;
	
	private String content;
	
	private String type;

	public UpdateRecordCommand(Long domainId, Long recordId, String name, String content, String type) {
		this.domainId = domainId;
		this.recordId = recordId;
		this.name = name;
		this.content = content;
		this.type = type;
	}
	
	@Override
	public boolean executeInSequence() {
		return false;
	}

	public Long getDomainId() {
		return this.domainId;
	}
	
	public Long getRecordId() {
		return this.recordId;
	}
	
	public String getName() {
		return this.name;
	}
	
	public String getContent() {
		return this.content;
	}
	
	public String getType() {
		return this.type;
	}
}
