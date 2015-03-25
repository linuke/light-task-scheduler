package com.lts.job.tracker.processor;

import com.lts.job.core.cluster.NodeType;
import com.lts.job.core.constant.Constants;
import com.lts.job.core.protocol.JobProtos;
import com.lts.job.core.protocol.command.AbstractCommandBody;
import com.lts.job.core.remoting.RemotingServerDelegate;
import com.lts.job.remoting.exception.RemotingCommandException;
import com.lts.job.remoting.netty.NettyRequestProcessor;
import com.lts.job.remoting.protocol.RemotingCommand;
import com.lts.job.remoting.protocol.RemotingProtos;
import com.lts.job.tracker.channel.ChannelManager;
import com.lts.job.tracker.channel.ChannelWrapper;
import io.netty.channel.ChannelHandlerContext;

import java.util.HashMap;
import java.util.Map;

import static com.lts.job.core.protocol.JobProtos.RequestCode;
import static com.lts.job.core.protocol.JobProtos.RequestCode.*;

/**
 * @author Robert HG (254963746@qq.com) on 7/23/14.
 *         job tracker 总的处理器, 每一种命令对应不同的处理器
 */
public class RemotingDispatcher extends AbstractProcessor {

    private final Map<RequestCode, NettyRequestProcessor> processors = new HashMap<RequestCode, NettyRequestProcessor>();
    private ChannelManager channelManager;

    public RemotingDispatcher(RemotingServerDelegate remotingServer) {
        super(remotingServer);
        this.channelManager = remotingServer.getApplication().getAttribute(Constants.CHANNEL_MANAGER);
        processors.put(SUBMIT_JOB, new JobSubmitProcessor(remotingServer));
        processors.put(JOB_FINISHED, new JobFinishedProcessor(remotingServer));
        processors.put(JOB_PULL, new JobPullProcessor(remotingServer));
    }

    @Override
    public RemotingCommand processRequest(ChannelHandlerContext ctx, RemotingCommand request) throws RemotingCommandException {
        // 心跳
        if (request.getCode() == JobProtos.RequestCode.HEART_BEAT.code()) {
            commonHandler(ctx, request);
            return RemotingCommand.createResponseCommand(JobProtos.ResponseCode.HEART_BEAT_SUCCESS.code(), "");
        }

        // 其他的请求code
        RequestCode code = valueOf(request.getCode());
        NettyRequestProcessor processor = processors.get(code);
        if (processor == null) {
            return RemotingCommand.createResponseCommand(RemotingProtos.ResponseCode.REQUEST_CODE_NOT_SUPPORTED.code(), "request code not supported!");
        }
        commonHandler(ctx, request);
        return processor.processRequest(ctx, request);
    }

    /**
     * 1. 将 channel 纳入管理中(不存在就加入)
     * 2. 更新 TaskTracker 节点信息(可用线程数)
     *
     * @param ctx
     * @param request
     */
    private void commonHandler(ChannelHandlerContext ctx, RemotingCommand request) {
        AbstractCommandBody commandBody = request.getBody();
        String nodeGroup = commandBody.getNodeGroup();
        String identity = commandBody.getIdentity();
        NodeType nodeType = NodeType.valueOf(commandBody.getNodeType());

        // 1. 将 channel 纳入管理中(不存在就加入)
        channelManager.offerChannel(new ChannelWrapper(ctx.channel(), nodeType, nodeGroup, identity));
    }

}
