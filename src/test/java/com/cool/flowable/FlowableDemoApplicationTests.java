package com.cool.flowable;

import org.flowable.engine.*;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.idm.api.Group;
import org.flowable.idm.api.User;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SpringBootTest
class FlowableDemoApplicationTests {
	@Autowired
	private ProcessEngine processEngine;
	@Autowired
	private RepositoryService repositoryService;
	@Autowired
	private RuntimeService runtimeService;
	@Autowired
	private TaskService taskService;
	@Autowired
	private HistoryService historyService;
	@Autowired
	private IdentityService identityService;

	/**
	 * 流程部署
	 */
	@Test
	void deployTest() {
		//流程部署
		Deployment deployment = repositoryService.createDeployment()
				.addClasspathResource("holiday-request.bpmn20.xml")
				.name("myRequest")
				.deploy();
		System.out.println("deployment.getId() = " + deployment.getId());//c0ee647c-0327-11ed-971f-ca21ded86ff7
		System.out.println("deployment.getName() = " + deployment.getName());//myRequest
		//流程定义
		ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
				.deploymentId(deployment.getId())
				.singleResult();
		System.out.println("process definition : " + processDefinition.getName());//Holiday Request
	}

	/**
	 * 	创建实例
	 */
	@Test
	void createInstanceTest(){
		Map<String, Object> variables = new HashMap<String, Object>();
		variables.put("employee", "zhangsan");
		variables.put("nrOfHolidays", "3天");
		variables.put("description", "想出去玩");
		ProcessInstance processInstance =
				runtimeService.startProcessInstanceByKey("holidayRequest", variables);
		System.out.println("processInstance.getId() = " + processInstance.getId());//7e883d22-fce9-11ec-a70e-9a840034e2f9
	}

	/**
	 * 创建用户，组并建立关系，涉及表结构ACT_ID_*
	 */
	@Test
	public void createUserAndGroupTest(){
		User lisi = identityService.newUser("lisi");
		User wangwu = identityService.newUser("wangwu");
		identityService.saveUser(lisi);
		identityService.saveUser(wangwu);
		Group managers = identityService.newGroup("managers");
		identityService.saveGroup(managers);
		List<User> userList = identityService.createUserQuery().list();
		for (User user : userList) {
			identityService.createMembership(user.getId(), managers.getId());
		}
	}

	/**
	 * 用户组成员拾取任务
	 */
	@Test
	public void clamTask(){
		Group managers = identityService.createGroupQuery().groupId("managers").singleResult();
		User lisi = identityService.createUserQuery().userId("lisi").singleResult();
		Task task = taskService.createTaskQuery().processInstanceId("204fac75-ff33-11ec-aa98-32435d83f9e9").
				taskCandidateGroup(managers.getId()).singleResult();
		if(task != null){
			taskService.claim(task.getId(),lisi.getId());
		}
	}

	/**
	 * 	处理流程，经理同意请假
	 */
	@Test
	void approveTaskTest(){
		Task task = taskService.createTaskQuery()
				.processInstanceId("204fac75-ff33-11ec-aa98-32435d83f9e9")
				.taskAssignee("lisi").singleResult();
		Map<String, Object> processVariables = taskService.getVariables(task.getId());
		System.out.println(processVariables.get("employee") + " wants " +
				processVariables.get("nrOfHolidays") + " of holidays.");
		Map<String, Object> variables = new HashMap<String, Object>();
		variables = new HashMap<String, Object>();
		variables.put("approved", true);
		taskService.complete(task.getId(), variables);
	}

	/**
	 * 	流程结束，清空ACT_RU_*数据库相关数据
	 */
	@Test
	public void completeTaskTest(){
		Task task = taskService.createTaskQuery().
		processInstanceId("204fac75-ff33-11ec-aa98-32435d83f9e9")
				.taskAssignee("zhangsan").singleResult();
		taskService.complete(task.getId());
	}

	/**
	 * 流程驳回
	 */
	@Test
	public void backTaskTest(){
		runtimeService.createChangeActivityStateBuilder()
				.processInstanceId("204fac75-ff33-11ec-aa98-32435d83f9e9")
				.moveActivityIdTo("holidayApprovedTask","approveTask")
				.changeState();
	}

	/**
	 * 查看运行期间的历史数据
	 */
	@Test
	public void queryHistory(){
		List<HistoricActivityInstance> activities =
				historyService.createHistoricActivityInstanceQuery()
						.processInstanceId("7e883d22-fce9-11ec-a70e-9a840034e2f9")
						.finished()
						.orderByHistoricActivityInstanceEndTime().asc()
						.list();
		for (HistoricActivityInstance activity : activities) {
			System.out.println(activity.getActivityId() + " took "
					+ activity.getDurationInMillis() + " milliseconds");
		}
	}

}
