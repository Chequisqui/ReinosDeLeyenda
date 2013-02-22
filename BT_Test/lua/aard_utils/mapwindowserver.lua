
require("serialize")

TextTree = luajava.bindClass("com.offsetnull.bt.window.TextTree")
mapWindow = GetWindowTokenByName("map_window")
buffer = luajava.new(TextTree)
buffer:setLinkify(false)
--buffer:setBold(true)
--buffer:setLineBreakAt(80)
mapstarted = false
function startMapCapture(name,line,map) 
	--Note("\nSTARTING MAP CAPTURE\n")
	EnableTrigger("map_capture",true)
	str = TextTree:deColorLine(line)
	--debugPrint("processing line: "..str:toString())
	mapstarted = true
	titlefound = false
	headers.title = ""
	currentline = 19
	buffer:empty()
	return true
end


headers = {}
headers.title = nil
headers.exits = nil

currentline = 19
titlefound = false
function doMapCapture(name,line,map)
	--local tmptmp = TextTree:deColorLine(line)
	--Note(string.format("\n%2d:%s|",currentline,tmptmp:toString()))
	if(mapstarted) then
		local titletmp = TextTree:deColorLine(line)
		if(titlefound) then
			headers.title = headers.title .. titletmp:toString()
			
			currentline = currentline -1
		else
			headers.title = titletmp:toString()
			--Note("\npulling title line:"..headers.title.."\n")
			titlefound = true
		end
		
		--Note("\nmapwindowdebug:|"..titletmp:toString().."|")
		if(titletmp:toString() == " ") then
			--debugPrint("found whitespace")
			--titlefound = true
			buffer:appendLine(line)
			mapstarted = false
			--Note("\npulling title line:"..headers.title.."\n")
			currentline = currentline - 1
		end
		
		
	else
		if(currentline == 0) then
		
			local exitstmp = TextTree:deColorLine(line)
			headers.exits = exitstmp:toString()
			--Note("\n"..currentline.."pulling footer line:"..headers.exits.."\n")
			
		--elseif(currentline >=2 and currentline <= 3) then
		--actually we just want to gag these for our own purpose.
		--	debugPrint("line gagged"..currentline)
		--elseif(currentline <= 19 and currentline >=16) then
		--gag these too 
		--debugPrint("line gagged"..currentline)
		else
			--local dbgstr = TextTree:deColorLine(line)
			--debugPrint(currentline.."pulling regular map line:"..dbgstr:toString())
			buffer:appendLine(line)
		end
		currentline = currentline -1
	end
	
	
	

end

function endMapCapture()
	local count = buffer:getBrokenLineCount()
	if(count < 5) then
		Note("ENDING MAP CAPTURE:"..buffer:getBrokenLineCount())
	end
	EnableTrigger("map_capture",false)
	
	mapWindow:setBuffer(buffer)
	
	WindowXCallS("map_window","updateHeaders",serialize(headers))
	
	InvalidateWindowText("map_window")
	--DrawWindow("map_window")
	--debugPrint("ending map capturing and drawing window")
	--buffer:empty()
	return true
end
--NewWindow("map_window",880,177,400,400)
--WindowBuffer("map_window",true)