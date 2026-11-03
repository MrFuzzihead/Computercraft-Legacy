-- Utilities for working with events.
--
-- Internal: this module may change or disappear. Programs should not require it.
--
-- Ported from CC:Tweaked's cc/internal/event.lua (MPL-2.0, see NOTICE) so that programs which
-- consume a key press (help's "press Q to exit", for instance) do not leave the matching `char`
-- event behind to land on the shell prompt as a stray command.

-- Discard a `char` event that may follow a `key` event.
--
-- The event queue is flushed by waiting one tick, stopping early if another key/char arrives. A key
-- and its char are occasionally delivered in different ticks, but that is rare enough that the
-- extra delay is not worth paying on every keypress.
local function discard_char()
    local timer = os.startTimer(0)
    while true do
        local event, id = os.pullEvent()
        if event == "timer" and id == timer then
            break
        elseif event == "char" or event == "key" or event == "key_up" then
            os.cancelTimer(timer)
            break
        end
    end
end

return { discard_char = discard_char }
