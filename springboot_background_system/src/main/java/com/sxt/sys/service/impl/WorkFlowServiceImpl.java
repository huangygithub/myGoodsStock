package com.sxt.sys.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

import org.activiti.bpmn.model.BpmnModel;
import org.activiti.engine.FormService;
import org.activiti.engine.HistoryService;
import org.activiti.engine.IdentityService;
import org.activiti.engine.ManagementService;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.ProcessEngineConfiguration;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.history.HistoricActivityInstance;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.history.HistoricTaskInstance;
import org.activiti.engine.impl.RepositoryServiceImpl;
import org.activiti.engine.impl.RuntimeServiceImpl;
import org.activiti.engine.impl.identity.Authentication;
import org.activiti.engine.impl.interceptor.Command;
import org.activiti.engine.impl.interceptor.CommandContext;
import org.activiti.engine.impl.persistence.entity.ExecutionEntity;
import org.activiti.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.activiti.engine.impl.pvm.PvmTransition;
import org.activiti.engine.impl.pvm.ReadOnlyProcessDefinition;
import org.activiti.engine.impl.pvm.process.ActivityImpl;
import org.activiti.engine.impl.pvm.process.ProcessDefinitionImpl;
import org.activiti.engine.impl.pvm.process.TransitionImpl;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.runtime.Execution;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Comment;
import org.activiti.engine.task.Task;
import org.activiti.image.ProcessDiagramGenerator;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sxt.sys.Vo.WorkFlowVo;
import com.sxt.sys.Vo.act.ActCommentEntity;
import com.sxt.sys.Vo.act.ActDeloymentEntity;
import com.sxt.sys.Vo.act.ActProcessDefinitionEntity;
import com.sxt.sys.Vo.act.ActTaskEntity;
import com.sxt.sys.common.Constast;
import com.sxt.sys.common.DataGridView;
import com.sxt.sys.common.WebUtils;
import com.sxt.sys.domain.Leavebill;
import com.sxt.sys.domain.User;
import com.sxt.sys.jump.JDJumpTaskCmd;
import com.sxt.sys.service.LeavebillService;
import com.sxt.sys.service.WorkFlowService;

import cn.hutool.core.util.RandomUtil;

@Service
@Transactional
public class WorkFlowServiceImpl implements WorkFlowService {
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
	@Autowired
	private FormService formService;
	@Autowired
	private ManagementService managementService;
	@Autowired
	private LeavebillService leavebillService;
	@Autowired
	private ProcessEngine processEngine;

	/**
	 * 查询流程部署信息
	 */
	public DataGridView queryProcessDeploy(WorkFlowVo workFlowVo) {
		if (workFlowVo.getDeploymentName() == null) {
			workFlowVo.setDeploymentName("");
		}
		String name = workFlowVo.getDeploymentName();
		// 查询总条数
		long count = repositoryService.createDeploymentQuery().deploymentNameLike("%" + name + "%").count();
		// 分页查询
		int firstResult = (workFlowVo.getPage() - 1) * workFlowVo.getLimit();
		int maxResults = workFlowVo.getLimit();
		List<Deployment> list = repositoryService.createDeploymentQuery().deploymentNameLike("%" + name + "%")
				.listPage(firstResult, maxResults);
		List<ActDeloymentEntity> data = new ArrayList<>();
		for (Deployment deployment : list) {
			ActDeloymentEntity entity = new ActDeloymentEntity();
			BeanUtils.copyProperties(deployment, entity);
			data.add(entity);
		}

		return new DataGridView(count, data);
	}

	@Override
	public DataGridView queryAllProcessDefinition(WorkFlowVo workFlowVo) {
		if (workFlowVo.getDeploymentName() == null) {
			workFlowVo.setDeploymentName("");
		}
		String name = workFlowVo.getDeploymentName();
		// 因为流程定义没根据流程名称查询的 先根据部署的名称模糊查询出所有的部署的ID
		List<Deployment> deploymentListList = repositoryService.createDeploymentQuery()
				.deploymentNameLike("%" + name + "%").list();
		HashSet<String> deploymentIds = new HashSet<>();
		for (Deployment deployment : deploymentListList) {
			deploymentIds.add(deployment.getId());
		}
		long count = 0;
		List<ActProcessDefinitionEntity> data = new ArrayList<>();
		if (deploymentIds.size() > 0) {
			// 查询总条数
			count = this.repositoryService.createProcessDefinitionQuery().deploymentIds(deploymentIds).count();
			// 分页查询
			int firstResult = (workFlowVo.getPage() - 1) * workFlowVo.getLimit();
			int maxResults = workFlowVo.getLimit();
			List<ProcessDefinition> list = this.repositoryService.createProcessDefinitionQuery()
					.deploymentIds(deploymentIds).listPage(firstResult, maxResults);
			for (ProcessDefinition processDefinition : list) {
				ActProcessDefinitionEntity entity = new ActProcessDefinitionEntity();
				BeanUtils.copyProperties(processDefinition, entity);
				data.add(entity);
			}

		}
		return new DataGridView(count, data);
	}

	// 部署流程
	@Override
	public void addWorkFlow(InputStream inputStream, String deploymentName) {
		ZipInputStream zipInputStream = new ZipInputStream(inputStream);
		this.repositoryService.createDeployment().name(deploymentName).addZipInputStream(zipInputStream).deploy();
		try {
			zipInputStream.close();
			inputStream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	@Override
	public Boolean deleteWorkFlow(String deploymentId) {
		// 删除有两种 一种是暴力删除 不管流程中有没有人走完直接删除 一种是普通删除 如果有人没走完流程抛异常不能删除
		// 普通删除
		// 1根据部署ID查处流程定义
		ProcessDefinition processDefinition = this.repositoryService.createProcessDefinitionQuery()
				.deploymentId(deploymentId).singleResult();
		// 2.取出流程定义ID
		String processDefinitionId = processDefinition.getId();
		// 3.根据流程定义ID查询是否有执行中的任务
		long count = this.taskService.createTaskQuery().processDefinitionId(processDefinitionId).count();
		if (count == 0) {
			this.repositoryService.deleteDeployment(deploymentId);
			return true;
		} else {
			return false;
		}

	}

	@Override
	public InputStream queryProcessDeploymentImage(String deploymentId) {
		// 1.根据部署ID查询流程定义对象
		ProcessDefinition processDefinition = this.repositoryService.createProcessDefinitionQuery()
				.deploymentId(deploymentId).singleResult();
		// 2.从流程定义对象里面得到图片的名称
		String resourceName = processDefinition.getDiagramResourceName();
		// 3.使用部署ID和图片名称去查询图片
		InputStream resourceAsStream = this.repositoryService.getResourceAsStream(deploymentId, resourceName);
		return resourceAsStream;
	}

	@Override
	public void startProcess(Integer leaveBillId) {
		// 找到流程的Key
		String processDefinitionKey = Leavebill.class.getSimpleName();
		String bussinessKey = processDefinitionKey + ":" + leaveBillId;// LeaveBill:11
		// 设置流程变量去设置下个流程的办理人
		HashMap<String, Object> variables = new HashMap<>();
		User user = (User) WebUtils.getSession().getAttribute("user");
		variables.put("username", user.getName());
		variables.put("boss", "李四");
		variables.put("personnel", "赵六");
		this.runtimeService.startProcessInstanceByKey(processDefinitionKey, bussinessKey, variables);
		// 更新请假单的状态
		Leavebill leavebill = this.leavebillService.getById(leaveBillId);
		leavebill.setState(Constast.STATE_LEAVEBILL_ONE);
		this.leavebillService.updateById(leavebill);
	}

	@Override
	public DataGridView queryCurrentUserTask(WorkFlowVo workFlowVo) {
		// 得到办理人信息
		User user = (User) WebUtils.getSession().getAttribute("user");
		String assignee = user.getName();
		// 查询总数
		long count = taskService.createTaskQuery().taskAssignee(assignee).count();
		// 查询集合
		int firstResult = (workFlowVo.getPage() - 1) * workFlowVo.getLimit();
		int maxResults = workFlowVo.getLimit();
		List<Task> list = this.taskService.createTaskQuery().taskAssignee(assignee).listPage(firstResult, maxResults);
		List<ActTaskEntity> data = new ArrayList<>();
		for (Task task : list) {
			ActTaskEntity entity = new ActTaskEntity();
			BeanUtils.copyProperties(task, entity);
			data.add(entity);
		}
		return new DataGridView(count, data);
	}

	@Override
	public Leavebill queryLeaveBillByTaskId(String taskId) {
		// 1.根据任务ID查询任务实例
		Task task = this.taskService.createTaskQuery().taskId(taskId).singleResult();
		// 2.从任务里面取出(单一流程)流程实例ID 多流程取执行实例ID task.getExecutionId()
		String processInstanceId = task.getProcessInstanceId();
		// 3.根据流程实例ID查询流程实例
		ProcessInstance processInstance = this.runtimeService.createProcessInstanceQuery()
				.processInstanceId(processInstanceId).singleResult();
		// 4.取出business_key
		String businessKey = processInstance.getBusinessKey();// LeaveBill:9
		String leaveBillId = businessKey.split(":")[1];
		return this.leavebillService.getById(Integer.valueOf(leaveBillId));
	}

	@Override
	public List<String> queryOutComeByTaskId(String taskId) {
		List<String> names = new ArrayList<>();
		// 1,根据任务ID查询任务实例
		Task task = this.taskService.createTaskQuery().taskId(taskId).singleResult();
		// 2,取出流程定义ID
		String processDefinitionId = task.getProcessDefinitionId();
		// 3,取出流程实例ID
		String processInstanceId = task.getProcessInstanceId();
		// 4,根据流程实例ID查询流程实例
		ProcessInstance processInstance = this.runtimeService.createProcessInstanceQuery()
				.processInstanceId(processInstanceId).singleResult();
		// 5,根据流程定义ID查询流程定义的XML信息
		ProcessDefinitionEntity processDefinition = (ProcessDefinitionEntity) this.repositoryService
				.getProcessDefinition(processDefinitionId);
		// 6,从流程实例对象里面取出当前活动节点ID
		String activityId = processInstance.getActivityId();// usertask1
		// 7,使用活动ID取出xml和当前活动ID相关节点数据
		ActivityImpl activityImpl = processDefinition.findActivity(activityId);
		// 8,从activityImpl取出连线信息
		List<PvmTransition> transitions = activityImpl.getOutgoingTransitions();
		if (null != transitions && transitions.size() > 0) {
			// PvmTransition就是连接对象
			for (PvmTransition pvmTransition : transitions) {
				String name = pvmTransition.getProperty("name").toString();
				names.add(name);
			}
		}
		return names;
	}

	/**
	 * 查询批注内容
	 * @param taskId
	 * @return
	 */
	@Override
	public DataGridView queryCommentByTaskId(String taskId) {
		// 1,根据任务ID查询任务实例
		Task task = this.taskService.createTaskQuery().taskId(taskId).singleResult();
		// 2,取出流程实例ID
		String processDefinitionId = task.getProcessInstanceId();
		List<Comment> comments = this.taskService.getProcessInstanceComments(processDefinitionId);
		List<ActCommentEntity> data = new ArrayList<>();
		for (Comment comment : comments) {
			ActCommentEntity entity = new ActCommentEntity();
			BeanUtils.copyProperties(comment, entity);
			data.add(entity);
		}
		return new DataGridView(Long.valueOf(data.size()), data);
	}

	/**
	 * 完成任务
	 * @param workFlowVo
	 * @return
	 */
	@Override
	public void completeTask(WorkFlowVo workFlowVo) {
		String taskId = workFlowVo.getTaskId();// 任务ID
//		String outcome = workFlowVo.getOutcome();// 连接名称
		Integer leaveBillId = workFlowVo.getId();// 请假单ID
		String comment = workFlowVo.getComment();// 批注信息

		// 1,根据任务ID查询任务实例
		Task task = this.taskService.createTaskQuery().taskId(taskId).singleResult();
		// 2,从任务里面取出流程实例ID
		String processInstanceId = task.getProcessInstanceId();
		// 设置批注人名
		User user = (User) WebUtils.getSession().getAttribute("user");
		/*
		 * 因为批注人是org.activiti.engine.impl.cmd.AddCommentCmd 80代码使用 String userId =
		 * Authentication.getAuthenticatedUserId(); CommentEntity comment = new
		 * CommentEntity(); comment.setUserId(userId);
		 * Authentication这类里面使用了一个ThreadLocal的线程局部变量
		 */
		Authentication.setAuthenticatedUserId(user.getName());
		// 添加批注信息 批注信息是没有批注人的 所以要通过上面的线程局部变量设置
		this.taskService.addComment(taskId, processInstanceId, "[同意]" + comment);

		// 完成任务
		Map<String, Object> variables = new HashMap<>();
		variables.put("level", "M");
		variables.put("days", "5");
		variables.put("position", "1");
		this.taskService.complete(taskId, variables);
		// 判断任务是否结束
		ProcessInstance processInstance = this.runtimeService.createProcessInstanceQuery()
				.processInstanceId(processInstanceId).singleResult();
		if (null == processInstance) {
			Leavebill leavebill = new Leavebill();
			leavebill.setId(leaveBillId);
			// 说明流程结束
//			if (outcome.equals("放弃")) {
//				leavebill.setState(Constast.STATE_LEAVEBILL_THREE);// 已放弃
//			} else {
			leavebill.setState(Constast.STATE_LEAVEBILL_TOW);// 审批完成
//			}
			this.leavebillService.updateById(leavebill);
		}
	}

	@Override
	public ProcessDefinition queryProcessDefinitionByTaskId(String taskId) {
		// 1,根据任务ID查询任务实例
		Task task = this.taskService.createTaskQuery().taskId(taskId).singleResult();
		String processDefinitionId = task.getProcessDefinitionId();
//        //2,取出流程实例ID
//        String processInstanceId = task.getProcessInstanceId();
//        //3,根据流程实例ID查询流程实例对象
//        ProcessInstance processInstance = this.runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId).singleResult();
//        //4,取出流程部署ID
//        String processDefinitionId = processInstance.getProcessDefinitionId();
		// 5,查询流程定义对象
		ProcessDefinition processDefinition = this.repositoryService.createProcessDefinitionQuery()
				.processDefinitionId(processDefinitionId).singleResult();
		return processDefinition;
	}

	@Override
	public Map<String, Object> queryTaskCoordinateByTaskId(String taskId) {
		Map<String, Object> coordinate = new HashMap<>();
		// 1,根据任务ID查询任务实例
		Task task = this.taskService.createTaskQuery().taskId(taskId).singleResult();
		// 2,取出流程定义ID
		String processDefinitionId = task.getProcessDefinitionId();
		// 3,取出流程实例ID
		String processInstanceId = task.getProcessInstanceId();
		// 4,根据流程实例ID查询流程实例
		ProcessInstance processInstance = this.runtimeService.createProcessInstanceQuery()
				.processInstanceId(processInstanceId).singleResult();
		// 5,根据流程定义ID查询流程定义的XML信息
		ProcessDefinitionEntity processDefinition = (ProcessDefinitionEntity) this.repositoryService
				.getProcessDefinition(processDefinitionId);
		// 6,从流程实例对象里面取出当前活动节点ID
		String activityId = processInstance.getActivityId();// usertask1
		// 7,使用活动ID取出xml和当前活动ID相关节点数据
		ActivityImpl activityImpl = processDefinition.findActivity(activityId);
		// 8,从activityImpl取出坐标信息
		coordinate.put("x", activityImpl.getX());
		coordinate.put("y", activityImpl.getY());
		coordinate.put("width", activityImpl.getWidth());
		coordinate.put("height", activityImpl.getHeight());
		return coordinate;
	}

	@Override
	public DataGridView queryCommentByLeaveBill(Integer id) {
		// 组装businessKey
		String businessKey = Leavebill.class.getSimpleName() + ":" + id;
		// 根据业务ID查询历史流星实例
		HistoricProcessInstance historicProcessInstance = this.historyService.createHistoricProcessInstanceQuery()
				.processInstanceBusinessKey(businessKey).singleResult();
		// 使用taskservice和流程实例iD查询批注
		List<Comment> comments = this.taskService.getProcessInstanceComments(historicProcessInstance.getId());
		List<ActCommentEntity> data = new ArrayList<>();
		for (Comment comment : comments) {
			ActCommentEntity entity = new ActCommentEntity();
			BeanUtils.copyProperties(comment, entity);
			data.add(entity);
		}
		return new DataGridView(Long.valueOf(data.size()), data);
	}

	/**
	 * 查询我的历史审批记录
	 */
	public DataGridView queryCurrentUserHistoryTask(WorkFlowVo workFlowVo) {
		User user = (User) WebUtils.getSession().getAttribute("user");
		String assignee = user.getName();
		// 查询总数
		long count = this.historyService.createHistoricTaskInstanceQuery().taskAssignee(assignee).count();
		// 查询集合
		int firstResult = (workFlowVo.getPage() - 1) * workFlowVo.getLimit();
		int maxResults = workFlowVo.getLimit();
		List<HistoricTaskInstance> data = this.historyService.createHistoricTaskInstanceQuery().taskAssignee(assignee)
				.orderByTaskCreateTime().desc().listPage(firstResult, maxResults);
		return new DataGridView(count, data);
	}

	/**
	 * 根据实例ID查询Busnesskey
	 */
	public Leavebill queryBusinessKeyByProcessInstanceId(String processInstance) {
		HistoricProcessInstance historicProcessInstance = this.historyService.createHistoricProcessInstanceQuery()
				.processInstanceId(processInstance).singleResult();
		String businessKey = historicProcessInstance.getBusinessKey();// LeaveBill:9
		String leaveBillId = businessKey.split(":")[1];
		return this.leavebillService.getById(Integer.valueOf(leaveBillId));
	}

	// *******************测试
	@Override
	public void rejectToTop(WorkFlowVo workFlowVo) {
		String leaveBillId = workFlowVo.getId().toString();
		// 组装businessKey
		String businessKey = Leavebill.class.getSimpleName() + ":" + leaveBillId;

		Map<String, Object> variables = new HashMap<>();
		// 获取当前任务
		Task currTask = taskService.createTaskQuery().processInstanceBusinessKey(businessKey).singleResult();

		// 获取流程实例
		ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
				.processInstanceId(currTask.getProcessInstanceId()).singleResult();
		// 获取流程定义
		ProcessDefinitionEntity processDefinitionEntity = (ProcessDefinitionEntity) ((RepositoryServiceImpl) repositoryService)
				.getDeployedProcessDefinition(currTask.getProcessDefinitionId());
		if (processDefinitionEntity == null) {
			System.out.println("不存在的流程定义。");
		}
		User user = (User) WebUtils.getSession().getAttribute("user");
		Authentication.setAuthenticatedUserId(user.getName());// 批注人的名称 一定要写，不然查看的时候不知道人物信息
		taskService.addComment(currTask.getId(), currTask.getProcessInstanceId(), "[驳回]" + workFlowVo.getComment());

		// 获取当前activity
		ActivityImpl currActivity = ((ProcessDefinitionImpl) processDefinitionEntity)
				.findActivity(currTask.getTaskDefinitionKey());

		// 获取当前任务流入
		List<PvmTransition> histTransitionList = currActivity.getIncomingTransitions();

		// 清除当前活动出口
		List<PvmTransition> originPvmTransitionList = new ArrayList<PvmTransition>();
		List<PvmTransition> pvmTransitionList = currActivity.getOutgoingTransitions();
		for (PvmTransition pvmTransition : pvmTransitionList) {
			originPvmTransitionList.add(pvmTransition);
		}
		pvmTransitionList.clear();

		// 查找上一个user task节点
		List<HistoricActivityInstance> historicActivityInstances = historyService.createHistoricActivityInstanceQuery()
				.activityType("userTask").processInstanceId(processInstance.getId()).finished()
				.orderByHistoricActivityInstanceEndTime().asc().list();
		TransitionImpl transitionImpl = null;
		if (historicActivityInstances.size() > 0) {
			ActivityImpl lastActivity = ((ProcessDefinitionImpl) processDefinitionEntity)
					.findActivity(historicActivityInstances.get(0).getActivityId());
			// 创建当前任务的新出口 createOutgoingTransition(String taransitionId) taransitionId不能重复
			// 用activitiId是不现实的 只能驳回一次 第二次会报重复过度的错
			transitionImpl = currActivity.createOutgoingTransition("flow:" + RandomUtil.randomNumbers(3));
			transitionImpl.setDestination(lastActivity);
		} else {
			System.out.println("历史节点不存在。");
		}
		variables = processInstance.getProcessVariables();
		// 完成任务
		List<Task> tasks = taskService.createTaskQuery().processInstanceId(processInstance.getId())
				.taskDefinitionKey(currTask.getTaskDefinitionKey()).list();
		for (Task task : tasks) {
			taskService.complete(task.getId(), variables);
		}

		// 移除新方向 恢复方向
		currActivity.getOutgoingTransitions().remove(transitionImpl);

		for (PvmTransition pvmTransition : originPvmTransitionList) {
			pvmTransitionList.add(pvmTransition);
		}
	}

	/**
	  *  根据ActivityId 查询出来想要活动Activity
	  * @param id
	  * @return
	  */
	public ActivityImpl queryTargetActivity(String id) {

		RepositoryService repositoryService = processEngine.getRepositoryService();
		ReadOnlyProcessDefinition deployedProcessDefinition = (ProcessDefinitionEntity) ((RepositoryServiceImpl) repositoryService)
				.getDeployedProcessDefinition("ziyouliu:1:4");
		List<ActivityImpl> activities = (List<ActivityImpl>) deployedProcessDefinition.getActivities();
		for (ActivityImpl activityImpl : activities) {
			if (activityImpl.getId().equals(id)) {
				return activityImpl;
			}
		}
		return null;
	}

	/**
	 * 查询当前的活动节点
	 */
	public ActivityImpl qureyCurrentTask(String processDefinitionId) {
		RuntimeService runtimeService = processEngine.getRuntimeService();
//		String processDefinitionId="ziyouliu:1:4";
		Execution execution = runtimeService.createExecutionQuery().processDefinitionId(processDefinitionId)
				.singleResult();
		String activityId = execution.getActivityId();
		ActivityImpl currentActivity = queryTargetActivity(activityId);
		System.out.println(currentActivity.getId() + "" + currentActivity.getProperty("name"));
		return currentActivity;
	}

	/**
	 * 第一种自由跳转的方式:
	 *   这种方式是通过 重写命令  ，推荐这种方式进行实现(这种方式的跳转，最后可以通过taskDeleteReason 来进行查询 )
	 */
	public void jumpEndActivity(String processDefinitionId) {
		// 当前节点
		ActivityImpl currentActivityImpl = qureyCurrentTask("ziyouliu:1:4");
		// 目标节点
		ActivityImpl targetActivity = queryTargetActivity("shenchajigou");
		// 当前正在执行的流程实例Id
		final String executionId = "7501";

		((RuntimeServiceImpl) processEngine.getRuntimeService()).getCommandExecutor().execute(new Command<Object>() {
			public Object execute(CommandContext commandContext) {
				ExecutionEntity execution = commandContext.getExecutionEntityManager().findExecutionById(executionId);
				execution.destroyScope("jump"); //
				ProcessDefinitionImpl processDefinition = execution.getProcessDefinition();
				ActivityImpl findActivity = processDefinition.findActivity("endevent1");
				execution.executeActivity(findActivity);
				return execution;
			}

		});
		System.out.println("完成");
	}

	@Override
	public void jump() {
		Map<String, Object> vars = new HashMap<String, Object>();
		String[] v = { "shareniu1", "shareniu2", "shareniu3", "shareniu4" };
		vars.put("assigneeList", Arrays.asList(v));
		// 分享牛原创(尊重原创 转载对的时候第一行请注明，转载出处来自分享牛http://blog.csdn.net/qq_30739519)
		ReadOnlyProcessDefinition processDefinitionEntity = (ReadOnlyProcessDefinition) repositoryService
				.getProcessDefinition("Leavebill:1:2525");
		Authentication.setAuthenticatedUserId("王五转");// 批注人的名称 一定要写，不然查看的时候不知道人物信息
		taskService.addComment("45004", "15004", "usertask1跳usertask3");
		// 目标节点
		ActivityImpl destinationActivity = (ActivityImpl) processDefinitionEntity.findActivity("usertask1");
		String executionId = "15004";
		// 当前节点
		ActivityImpl currentActivity = (ActivityImpl) processDefinitionEntity.findActivity("usertask2");
		managementService.executeCommand(new JDJumpTaskCmd(executionId, destinationActivity, vars, currentActivity));

	}

	@Override
	public InputStream readResource(String processDefinitionId) {

		InputStream inputStream = null;
		BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);
		List<String> highLightedActivities = new ArrayList<String>();
		List<HistoricActivityInstance> historicActivityInstances = historyService.createHistoricActivityInstanceQuery()
				.processInstanceId("72501").orderByHistoricActivityInstanceStartTime().asc().list();
		for (HistoricActivityInstance historicActivityInstance : historicActivityInstances) {
			highLightedActivities.add(historicActivityInstance.getActivityId());
		}

		ProcessDefinitionEntity definitionEntity = (ProcessDefinitionEntity) repositoryService
				.getProcessDefinition(processDefinitionId);
		List<String> highLightedFlows = getHighLightedFlows(definitionEntity, historicActivityInstances);
		highLightedFlows.add("flow1");

		ProcessEngineConfiguration processEngineConfiguration = processEngine.getProcessEngineConfiguration();
		ProcessDiagramGenerator diagramGenerator = processEngineConfiguration.getProcessDiagramGenerator();
		inputStream = diagramGenerator.generateDiagram(bpmnModel, "jpg", highLightedActivities, highLightedFlows, "宋体",
				"宋体", "宋体", null, 1.0);
		return inputStream;
	}

	private List<String> getHighLightedFlows(ProcessDefinitionEntity processDefinitionEntity,
			List<HistoricActivityInstance> historicActivityInstances) {
		List<String> highFlows = new ArrayList<String>();// 用以保存高亮的线flowId
		for (int i = 0; i < historicActivityInstances.size() - 1; i++) {// 对历史流程节点进行遍历
			ActivityImpl activityImpl = processDefinitionEntity
					.findActivity(historicActivityInstances.get(i).getActivityId());// 得到节点定义的详细信息
			List<ActivityImpl> sameStartTimeNodes = new ArrayList<ActivityImpl>();// 用以保存后需开始时间相同的节点
			ActivityImpl sameActivityImpl1 = processDefinitionEntity
					.findActivity(historicActivityInstances.get(i + 1).getActivityId());
			// 将后面第一个节点放在时间相同节点的集合里
			sameStartTimeNodes.add(sameActivityImpl1);
			for (int j = i + 1; j < historicActivityInstances.size() - 1; j++) {
				HistoricActivityInstance activityImpl1 = historicActivityInstances.get(j);// 后续第一个节点
				HistoricActivityInstance activityImpl2 = historicActivityInstances.get(j + 1);// 后续第二个节点
				if (activityImpl1.getStartTime().equals(activityImpl2.getStartTime())) {
					// 如果第一个节点和第二个节点开始时间相同保存
					ActivityImpl sameActivityImpl2 = processDefinitionEntity
							.findActivity(activityImpl2.getActivityId());
					sameStartTimeNodes.add(sameActivityImpl2);
				} else {
					// 有不相同跳出循环
					break;
				}
			}
			List<PvmTransition> pvmTransitions = activityImpl.getOutgoingTransitions();// 取出节点的所有出去的线
			for (PvmTransition pvmTransition : pvmTransitions) {
				// 对所有的线进行遍历
				ActivityImpl pvmActivityImpl = (ActivityImpl) pvmTransition.getDestination();
				// 如果取出的线的目标节点存在时间相同的节点里，保存该线的id，进行高亮显示
				if (sameStartTimeNodes.contains(pvmActivityImpl)) {
					highFlows.add(pvmTransition.getId());
				}
			}
		}
		return highFlows;
	}

}
