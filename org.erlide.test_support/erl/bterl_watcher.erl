-module(bterl_watcher).

-export([start/0, 
         start_bterl/1,
         init_debugger/0
        ]).

-export([init/1, 
         start_failed/1,
         tc_result/1,
         tc_fail/1,
         log_started/1
        ]).


start() ->
	spawn(fun() ->
				  wait_start()
		  end).

%% shut down everything when there are no more jobs to do
stop_when_done() ->
	timer:sleep(1000),
	Jobs = test_server_ctrl:jobs(),
	case Jobs of
		[] ->
			init:stop();
		_ ->
			stop_when_done()
	end.

wait_start() ->
	timer:sleep(1000),
	Jobs = (catch test_server_ctrl:jobs()),
	case Jobs of
		[] ->
			wait_start();
		{'EXIT', _} ->
			wait_start();
		_ ->
			stop_when_done()
	end.

start_bterl(Str) ->
    erlide_log:log({start_bterl, Str}),
    {ok, {Flags, Cmd, Trace, Cb, Dir}=X} = erlide_backend:parse_term(Str++"."),
    erlide_log:log({start_bterl, X}),
%%     Pids = case bt_run:has_flag($t, Flags) of
%%         true ->
%%             Spec = [],
%%             TracePid = bt_run:start_trace(Dir, Spec),
%%             [TracePid];
%%         false ->
%%             []
%%     end,
    Pids = [],
    %bt_run:run(Flags, Cmd, Pids, ?MODULE),
      
    
    %% sent 'finished' event to caller
    ok.

init_debugger() ->
    %% FIXME: DON'T HARDCODE THIS!
    timer:sleep(1000),
    erlide_jrpc:event(bterl_debugger, self()),
    receive
        ok ->
            erlide_log:log("Debugger initialized!"),
            ok
    after 30000 ->
            erlide_log:log(warning, "Debugger initialization failed..."),
            nok
    end.


init(Args) ->
    
    os:cmd("touch /home/qvladum/zzz"),
    erlide_log:log(Args),
    ok.

start_failed(Reason) ->
    erlide_log:log(Reason),
    ok.

tc_result(Result) ->
    erlide_log:log({result, Result}),
    ok.

tc_fail(Result) ->
    erlide_log:log({fail, Result}),
    ok.

log_started(Arg) ->
    erlide_log:log(Arg),
    ok.