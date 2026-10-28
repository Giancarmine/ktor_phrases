<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Emoji phrases</title>
</head>
<body>
<h1>Emoji phrases</h1>
<#if phrases?size == 0>
    <p>No phrases yet. POST one to <code>/api/v1/phrases</code>.</p>
<#else>
    <p>Showing ${phrases?size?c} of ${total?c} (offset ${offset?c}, limit ${limit?c}).</p>
    <ul>
        <#list phrases as phrase>
            <li>
                <span>${phrase.emoji?html}</span>
                ${phrase.phrase?html}
                <small>#${phrase.id}</small>
            </li>
        </#list>
    </ul>
    <nav>
        <#if hasPrev>
            <a href="/phrases?limit=${limit?c}&amp;offset=${prevOffset?c}">Previous</a>
        </#if>
        <#if hasNext>
            <a href="/phrases?limit=${limit?c}&amp;offset=${nextOffset?c}">Next</a>
        </#if>
    </nav>
</#if>
</body>
</html>
