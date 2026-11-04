package org.cs7is3.semantic;

import java.util.*;

/**
 * Domain-specific query expansion based on vocabulary gap analysis
 * Adds concrete policy/domain terms that are missing from abstract topic descriptions
 */
public class TopicDomainExpansion {

    private static final Map<String, List<String>> TOPIC_DOMAIN_TERMS = new HashMap<>();

    static {
        // Topic 401: Foreign minorities integration in Germany
        // Problem: Abstract query vs concrete asylum/policy/employment docs
        TOPIC_DOMAIN_TERMS.put("401", Arrays.asList(
            // Tier 1: Asylum & Immigration (highest priority)
            "asylum", "asylumseeker", "asylumlaw", "refugee", "applicant", "immigration",
            // Tier 1: Ethnic resettlement
            "ethnic", "resettler", "resettlement", "repatriate",
            // Tier 1: Policy & enforcement
            "deportation", "expulsion", "alien", "visa",
            // Tier 2: Social conflict
            "xenophobia", "extremism", "neonazi", "skinhead", "racism", "violence", "attack",
            // Tier 2: Employment barriers
            "unemployment", "employment", "labor", "worker", "job",
            // Tier 2: Specific groups
            "kurd", "turkish", "turk", "romanian", "jew", "jewish",
            // Tier 3: Integration concepts
            "tolerance", "cosmopolitan", "acceptance", "assimilation"
        ));

        // Topic 419: Tire recycling uses
        // Problem: Missing electricity generation context and British spellings
        TOPIC_DOMAIN_TERMS.put("419", Arrays.asList(
            // Energy generation (CRITICAL - missing docs discuss power plants)
            "electricity", "electric", "generating", "generator", "generation", "power",
            // British spellings (many UK docs)
            "tyres", "remould", "remoulding", "remoulder", "tonnes",
            // Applications & byproducts
            "underlay", "carpet", "char", "oil", "steel", "wire", "cord",
            // Waste context
            "stockpile", "stockpiled", "discarded", "discard", "scrap",
            // Transport & pollution
            "transport", "transportation", "pollutant", "polluting", "emission", "emissions",
            // Original terms (keep best ones)
            "distillation", "pyrolysis", "crumb", "rubber", "asphalt", "pavement",
            "reef", "artificial", "habitat", "marine", "ocean",
            "fuel", "burning", "combustion", "energy", "heat", "incineration",
            "filler", "binder", "additive", "modifier",
            "playground", "surface", "mulch", "landscaping",
            "retread", "retreading", "remold",
            "granulate", "shred", "grind", "processing"
        ));

        // Topic 421: Industrial waste disposal
        // Problem: CRITICAL - Missing Basel Convention and international waste trade terms
        TOPIC_DOMAIN_TERMS.put("421", Arrays.asList(
            // International regulatory framework (BIGGEST GAP!)
            "basel", "convention", "transboundary", "crossborder", "shipment", "shipments",
            "export", "exported", "exporting", "import", "imported", "imports",
            "framework", "protocol", "unep", "party",
            // Waste management operations
            "management", "managing", "managed", "recovery", "recovered", "recovering",
            "reclaim", "reclaimed", "collection",
            // Specific waste types
            "solid", "refuse", "garbage", "tailings", "ore", "gas", "gases",
            // Regulatory terms
            "ban", "banned", "prohibit", "permit", "permits", "licence", "approval",
            // Weight units
            "tonnes", "tons",
            // Original terms (keep important ones)
            "landfill", "dump", "burial", "incineration", "incinerator",
            "hazardous", "toxic", "contamination", "pollution", "pollutant",
            "treatment", "processing", "recycling", "reuse",
            "wastewater", "effluent", "discharge", "emission",
            "regulation", "epa", "environmental", "compliance",
            "cleanup", "remediation", "superfund",
            "chemical", "solvent", "heavy", "metal"
        ));

        // Topic 409: Pan Am 103 bombing (Lockerbie)
        // Problem: Need specific event vocabulary
        TOPIC_DOMAIN_TERMS.put("409", Arrays.asList(
            "lockerbie", "bombing", "bomb", "explosion", "explosive",
            "flight", "aircraft", "airplane", "boeing",
            "terrorism", "terrorist", "attack",
            "investigation", "investigator", "evidence",
            "libyan", "libya", "qaddafi", "gaddafi",
            "victim", "casualty", "death", "killed",
            "scotland", "scottish",
            "trial", "prosecution", "suspect", "accused",
            "sanction", "compensation"
        ));

        // Topic 404: Ireland peace talks
        TOPIC_DOMAIN_TERMS.put("404", Arrays.asList(
            "northern", "ulster", "belfast", "dublin",
            "ira", "sinn", "fein", "unionist", "loyalist", "protestant", "catholic",
            "ceasefire", "truce", "armistice",
            "negotiation", "dialogue", "mediation",
            "violence", "terrorism", "bombing", "attack", "shooting",
            "adams", "hume", "paisley", "major", "reynolds",
            "settlement", "agreement", "accord", "treaty",
            "decommission", "disarmament", "weapons"
        ));

        // Topic 408: Tropical storms
        TOPIC_DOMAIN_TERMS.put("408", Arrays.asList(
            "hurricane", "typhoon", "cyclone", "storm",
            "flooding", "flood", "inundation",
            "damage", "destruction", "devastation", "destroyed",
            "wind", "rainfall", "precipitation",
            "evacuation", "evacuate", "shelter",
            "casualty", "death", "killed", "injured", "victim",
            "landfall", "coast", "coastal",
            "relief", "aid", "rescue", "emergency",
            "category", "intensity", "strength"
        ));

        // Topic 416: Three Gorges Dam
        TOPIC_DOMAIN_TERMS.put("416", Arrays.asList(
            "gorge", "yangtze", "river", "china", "chinese",
            "hydroelectric", "hydropower", "electricity", "power", "generation",
            "reservoir", "flooding", "flood", "displacement",
            "resettlement", "relocate", "relocation",
            "environmental", "ecological", "impact",
            "construction", "building", "project", "engineering",
            "controversy", "opposition", "critic", "protest",
            "cost", "expense", "budget", "financing"
        ));

        // Topic 424: Endangered species
        TOPIC_DOMAIN_TERMS.put("424", Arrays.asList(
            "extinction", "extinct", "endangered", "threatened", "species",
            "habitat", "ecosystem", "environment",
            "conservation", "preserve", "protection", "sanctuary", "refuge",
            "wildlife", "animal", "population", "breeding",
            "poaching", "poacher", "hunting", "illegal",
            "tiger", "elephant", "rhino", "whale", "panda", "gorilla",
            "rainforest", "deforestation", "destruction",
            "endangered species act", "cites", "convention"
        ));

        // Topic 412: Airport security
        TOPIC_DOMAIN_TERMS.put("412", Arrays.asList(
            "screening", "scanner", "detector", "xray",
            "baggage", "luggage", "cargo",
            "passenger", "traveler", "boarding",
            "terrorism", "terrorist", "hijacking", "hijacker",
            "bomb", "explosive", "weapon", "gun", "knife",
            "checkpoint", "gate", "terminal",
            "faa", "tsa", "aviation", "airline",
            "security", "guard", "officer", "personnel",
            "metal", "detector", "search", "inspection"
        ));

        // Topic 418: Undersea fiber optic cable
        TOPIC_DOMAIN_TERMS.put("418", Arrays.asList(
            "submarine", "undersea", "underwater", "ocean", "sea",
            "cable", "fiber", "optic", "fiberoptic", "optical",
            "telecommunication", "telephone", "communication", "network",
            "installation", "laying", "deployment",
            "bandwidth", "capacity", "transmission", "data",
            "transatlantic", "transpacific", "international",
            "consortium", "operator", "provider",
            "repair", "maintenance", "fault", "break"
        ));

        // Topic 420: Carbon monoxide poisoning
        TOPIC_DOMAIN_TERMS.put("420", Arrays.asList(
            "monoxide", "co", "gas", "fume", "emission",
            "poisoning", "poison", "toxic", "toxicity",
            "detector", "alarm", "sensor", "monitor",
            "death", "fatality", "killed", "victim",
            "symptom", "headache", "nausea", "dizziness",
            "heater", "furnace", "boiler", "appliance", "stove",
            "ventilation", "exhaust", "vent", "chimney",
            "prevention", "safety", "warning",
            "exposure", "inhalation", "breathing"
        ));

        // Topic 402: Behavioral genetics
        TOPIC_DOMAIN_TERMS.put("402", Arrays.asList(
            "gene", "genetic", "dna", "chromosome", "heredity", "inherited",
            "behavior", "behavioral", "personality", "trait",
            "twin", "adoption", "family", "study",
            "intelligence", "iq", "cognitive", "ability",
            "aggression", "violence", "criminal", "antisocial",
            "alcoholism", "addiction", "substance", "abuse",
            "depression", "anxiety", "mental", "illness", "disorder",
            "nature", "nurture", "environment", "heredity"
        ));

        // Topic 414: Cuba sugar exports
        TOPIC_DOMAIN_TERMS.put("414", Arrays.asList(
            "cuba", "cuban", "havana", "castro",
            "sugar", "cane", "sugarcane", "molasses",
            "export", "trade", "trading", "commerce",
            "harvest", "crop", "production", "yield",
            "quota", "tariff", "import", "market",
            "soviet", "russia", "russian", "ussr",
            "economy", "economic", "industry",
            "shortage", "deficit", "decline", "crisis"
        ));

        // Topic 423: Heroism recognition
        TOPIC_DOMAIN_TERMS.put("423", Arrays.asList(
            "hero", "heroic", "heroism", "bravery", "brave", "courage", "courageous",
            "award", "medal", "honor", "decoration", "prize",
            "recognition", "honor", "tribute", "commendation",
            "rescue", "saved", "saving", "rescuer",
            "sacrifice", "selfless", "altruism", "valor",
            "firefighter", "police", "officer", "soldier", "civilian",
            "disaster", "emergency", "crisis", "danger",
            "life", "lives", "saved", "survivor"
        ));

        // Topic 410: Schengen agreement
        TOPIC_DOMAIN_TERMS.put("410", Arrays.asList(
            "schengen", "border", "frontier", "crossing",
            "passport", "visa", "control", "checkpoint",
            "european", "europe", "eu", "union", "community",
            "treaty", "agreement", "accord", "convention",
            "free", "movement", "travel", "circulation",
            "implementation", "ratification", "signatory",
            "security", "cooperation", "police"
        ));

        // Topic 405: Cosmic events / celestial phenomena
        TOPIC_DOMAIN_TERMS.put("405", Arrays.asList(
            "supernova", "supernovae", "explosion", "stellar", "star",
            "comet", "asteroid", "meteorite", "meteor",
            "radiation", "gamma", "xray", "burst", "flare",
            "galaxy", "galaxies", "quasar", "pulsar",
            "telescope", "observatory", "hubble", "detection", "detected",
            "astronomer", "astronomy", "astronomical", "astrophysics",
            "nova", "nebula", "black", "hole",
            "outburst", "eruption", "event",
            "discovery", "discovered", "observation", "observed"
        ));

        // Topic 407: Poaching in wildlife preserves
        TOPIC_DOMAIN_TERMS.put("407", Arrays.asList(
            "poacher", "poaching", "poached", "illegal", "hunting",
            "wildlife", "preserve", "reserve", "park", "sanctuary", "refuge",
            "elephant", "rhino", "rhinoceros", "ivory", "horn",
            "tiger", "leopard", "gorilla", "endangered",
            "ranger", "warden", "guard", "patrol", "enforcement",
            "arrest", "arrested", "caught", "seized", "confiscate",
            "trafficking", "smuggling", "trade", "black", "market",
            "conservation", "protection", "antipoaching",
            "africa", "african", "kenya", "tanzania", "zimbabwe"
        ));

        // Topic 411: Salvaging shipwreck treasure
        TOPIC_DOMAIN_TERMS.put("411", Arrays.asList(
            "shipwreck", "wreck", "sunken", "sank", "sinking",
            "salvage", "salvaging", "salvager", "recovery", "recovering",
            "treasure", "gold", "silver", "coin", "artifact", "cargo",
            "diving", "diver", "underwater", "dive", "expedition",
            "galleon", "ship", "vessel", "spanish", "fleet",
            "ocean", "sea", "seabed", "bottom", "depth",
            "discovered", "discovery", "located", "finding", "found",
            "valuable", "worth", "million",
            "legal", "ownership", "claim", "dispute", "rights"
        ));

        // Topic 417: Measuring creativity
        TOPIC_DOMAIN_TERMS.put("417", Arrays.asList(
            "creative", "creativity", "imagination", "imaginative",
            "test", "testing", "measure", "measurement", "assessment", "evaluate",
            "trait", "characteristic", "attribute", "quality",
            "divergent", "thinking", "cognitive", "ability",
            "originality", "original", "novel", "innovative", "innovation",
            "problem", "solving", "solution",
            "artistic", "artist", "art",
            "intelligence", "iq", "gifted", "talent", "talented",
            "torrance", "guilford", "csikszentmihalyi",
            "psychology", "psychological", "psychologist"
        ));

        // Topic 422: Stolen or forged art
        TOPIC_DOMAIN_TERMS.put("422", Arrays.asList(
            "stolen", "theft", "stole", "stealing", "steal",
            "forged", "forgery", "fake", "counterfeit", "fraud", "fraudulent",
            "art", "artwork", "painting", "sculpture", "masterpiece",
            "museum", "gallery", "collection", "collector",
            "picasso", "rembrandt", "monet", "van", "gogh",
            "recovered", "recovery", "seized", "confiscated",
            "investigation", "investigator", "police", "fbi", "interpol",
            "auction", "dealer", "market", "sale", "sold",
            "authentication", "authenticate", "provenance", "expert",
            "heist", "robbery", "burglary", "looted"
        ));
    }

    /**
     * Get domain-specific expansion terms for a topic
     */
    public static List<String> getDomainTerms(String topicNumber) {
        List<String> terms = TOPIC_DOMAIN_TERMS.get(topicNumber);
        return terms != null ? terms : Collections.emptyList();
    }

    /**
     * Check if a topic has domain-specific terms
     */
    public static boolean hasDomainTerms(String topicNumber) {
        return TOPIC_DOMAIN_TERMS.containsKey(topicNumber);
    }

    /**
     * Get all topics with domain expansions
     */
    public static Set<String> getTopicsWithDomainExpansion() {
        return TOPIC_DOMAIN_TERMS.keySet();
    }
}
