export function createSseParser(onEvent) {
  let buffer = ''
  let eventName = 'message'
  let dataLines = []

  const dispatch = () => {
    if (dataLines.length === 0) {
      eventName = 'message'
      return
    }

    const rawData = dataLines.join('\n')
    let data = rawData
    try {
      data = JSON.parse(rawData)
    } catch {
      // Plain text is allowed for compatibility with older stream responses.
    }
    onEvent({ event: eventName, data })
    eventName = 'message'
    dataLines = []
  }

  const processLine = (line) => {
    if (line.endsWith('\r')) line = line.slice(0, -1)
    if (line === '') {
      dispatch()
    } else if (line.startsWith('event:')) {
      eventName = line.slice(6).trim() || 'message'
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trimStart())
    }
  }

  return {
    feed(chunk) {
      buffer += chunk
      let newlineIndex
      while ((newlineIndex = buffer.indexOf('\n')) !== -1) {
        processLine(buffer.slice(0, newlineIndex))
        buffer = buffer.slice(newlineIndex + 1)
      }
    },
    end() {
      if (buffer) processLine(buffer)
      buffer = ''
      dispatch()
    }
  }
}
