def getAccountID(String environment){
    switch(environment) { 
        case 'dev': 
            return "688567303455"
        case 'qa':
            return "315069554701"
        case 'uat':
            return "315069644701"
        case 'pre-prod':
            return "315069354701"
        case 'prod':
            return "315063654701"
        default:
            return "nothing"
    } 
}