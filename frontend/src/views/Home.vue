<template>
  <div class="home">
    <!-- Header Section -->
    <header class="hero-section">
      <div class="hero-content">
        <h1 class="hero-title">
          Welcome to <span class="highlight">SkyFlow</span>
        </h1>
        <p class="hero-subtitle">
          Experience seamless airline management with modern technology
        </p>
        <div class="hero-actions">
          <el-button type="primary" size="large" @click="goToSearch">
            <el-icon><Search /></el-icon>
            Search Flights
          </el-button>
          <el-button size="large" @click="goToFlights">
            <el-icon><Plane /></el-icon>
            View All Flights
          </el-button>
        </div>
      </div>
      <div class="hero-image">
        <img src="@/assets/hero-plane.svg" alt="Airplane" />
      </div>
    </header>

    <!-- Features Section -->
    <section class="features-section">
      <div class="container">
        <h2 class="section-title">Why Choose SkyFlow?</h2>
        <div class="features-grid">
          <div class="feature-card" v-for="feature in features" :key="feature.id">
            <div class="feature-icon">
              <el-icon :size="48">
                <component :is="feature.icon" />
              </el-icon>
            </div>
            <h3 class="feature-title">{{ feature.title }}</h3>
            <p class="feature-description">{{ feature.description }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- Quick Search Section -->
    <section class="search-section">
      <div class="container">
        <h2 class="section-title">Find Your Perfect Flight</h2>
        <div class="search-form">
          <el-form :model="searchForm" :rules="searchRules" ref="searchFormRef">
            <div class="search-grid">
              <el-form-item prop="from">
                <el-autocomplete
                  v-model="searchForm.from"
                  :fetch-suggestions="queryCities"
                  placeholder="From"
                  clearable
                  class="search-input"
                >
                  <template #prefix>
                    <el-icon><Location /></el-icon>
                  </template>
                </el-autocomplete>
              </el-form-item>

              <el-form-item prop="to">
                <el-autocomplete
                  v-model="searchForm.to"
                  :fetch-suggestions="queryCities"
                  placeholder="To"
                  clearable
                  class="search-input"
                >
                  <template #prefix>
                    <el-icon><Location /></el-icon>
                  </template>
                </el-autocomplete>
              </el-form-item>

              <el-form-item prop="departureDate">
                <el-date-picker
                  v-model="searchForm.departureDate"
                  type="date"
                  placeholder="Departure Date"
                  format="YYYY-MM-DD"
                  value-format="YYYY-MM-DD"
                  class="search-input"
                />
              </el-form-item>

              <el-form-item prop="passengers">
                <el-select
                  v-model="searchForm.passengers"
                  placeholder="Passengers"
                  class="search-input"
                >
                  <el-option
                    v-for="i in 9"
                    :key="i"
                    :label="`${i} ${i === 1 ? 'Passenger' : 'Passengers'}`"
                    :value="i"
                  />
                </el-select>
              </el-form-item>
            </div>

            <div class="search-actions">
              <el-button type="primary" size="large" @click="handleSearch" :loading="searching">
                <el-icon><Search /></el-icon>
                Search Flights
              </el-button>
            </div>
          </el-form>
        </div>
      </div>
    </section>

    <!-- Popular Destinations -->
    <section class="destinations-section">
      <div class="container">
        <h2 class="section-title">Popular Destinations</h2>
        <div class="destinations-grid">
          <div
            class="destination-card"
            v-for="destination in popularDestinations"
            :key="destination.id"
            @click="goToDestination(destination)"
          >
            <div class="destination-image">
              <img :src="destination.image" :alt="destination.name" />
            </div>
            <div class="destination-content">
              <h3 class="destination-name">{{ destination.name }}</h3>
              <p class="destination-description">{{ destination.description }}</p>
              <div class="destination-price">
                <span class="price-label">From</span>
                <span class="price-amount">${{ destination.startingPrice }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Statistics Section -->
    <section class="stats-section">
      <div class="container">
        <div class="stats-grid">
          <div class="stat-item" v-for="stat in statistics" :key="stat.id">
            <div class="stat-number">
              <CountTo :end-val="stat.value" :duration="2000" />
              <span class="stat-suffix">{{ stat.suffix }}</span>
            </div>
            <p class="stat-label">{{ stat.label }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- CTA Section -->
    <section class="cta-section">
      <div class="container">
        <div class="cta-content">
          <h2>Ready to Start Your Journey?</h2>
          <p>Join thousands of satisfied customers who trust SkyFlow for their travel needs.</p>
          <div class="cta-actions">
            <el-button type="primary" size="large" @click="goToRegister">
              Get Started
            </el-button>
            <el-button size="large" @click="goToAbout">
              Learn More
            </el-button>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, Plane, Location } from '@element-plus/icons-vue'
import CountTo from 'vue3-count-to'
import { apiService } from '@/services/api'

const router = useRouter()

// Search form
const searchFormRef = ref()
const searching = ref(false)
const searchForm = reactive({
  from: '',
  to: '',
  departureDate: '',
  passengers: 1
})

const searchRules = {
  from: [{ required: true, message: 'Please select departure city', trigger: 'blur' }],
  to: [{ required: true, message: 'Please select arrival city', trigger: 'blur' }],
  departureDate: [{ required: true, message: 'Please select departure date', trigger: 'change' }],
  passengers: [{ required: true, message: 'Please select number of passengers', trigger: 'change' }]
}

// Features data
const features = ref([
  {
    id: 1,
    icon: 'Plane',
    title: 'Wide Flight Selection',
    description: 'Access to thousands of flights across multiple airlines and destinations.'
  },
  {
    id: 2,
    icon: 'Shield',
    title: 'Secure Payments',
    description: 'Industry-leading security with Stripe integration for safe transactions.'
  },
  {
    id: 3,
    icon: 'Clock',
    title: 'Real-time Updates',
    description: 'Get instant notifications about flight status and schedule changes.'
  },
  {
    id: 4,
    icon: 'Service',
    title: '24/7 Support',
    description: 'Round-the-clock customer support to assist you with any queries.'
  }
])

// Popular destinations
const popularDestinations = ref([
  {
    id: 1,
    name: 'New York',
    description: 'The city that never sleeps',
    image: '/images/destinations/new-york.jpg',
    startingPrice: 299
  },
  {
    id: 2,
    name: 'London',
    description: 'Historic and modern metropolis',
    image: '/images/destinations/london.jpg',
    startingPrice: 399
  },
  {
    id: 3,
    name: 'Tokyo',
    description: 'Where tradition meets innovation',
    image: '/images/destinations/tokyo.jpg',
    startingPrice: 599
  },
  {
    id: 4,
    name: 'Paris',
    description: 'The city of love and lights',
    image: '/images/destinations/paris.jpg',
    startingPrice: 349
  }
])

// Statistics
const statistics = ref([
  { id: 1, value: 10000, suffix: '+', label: 'Happy Customers' },
  { id: 2, value: 500, suffix: '+', label: 'Destinations' },
  { id: 3, value: 50, suffix: '+', label: 'Airlines' },
  { id: 4, value: 99, suffix: '%', label: 'Satisfaction Rate' }
])

// Methods
const queryCities = async (queryString: string, cb: Function) => {
  if (queryString.length === 0) {
    cb([])
    return
  }

  try {
    const response = await apiService.cities.getAll()
    const cities = response.data.data.filter((city: any) =>
      city.name.toLowerCase().includes(queryString.toLowerCase())
    )
    cb(cities.map((city: any) => ({ value: city.name, id: city.id })))
  } catch (error) {
    cb([])
  }
}

const handleSearch = async () => {
  try {
    await searchFormRef.value.validate()
    searching.value = true
    
    const searchParams = {
      from: searchForm.from,
      to: searchForm.to,
      departureDate: searchForm.departureDate,
      passengers: searchForm.passengers
    }
    
    router.push({
      name: 'FlightSearch',
      query: searchParams
    })
  } catch (error) {
    ElMessage.error('Please fill in all required fields')
  } finally {
    searching.value = false
  }
}

const goToSearch = () => {
  router.push({ name: 'FlightSearch' })
}

const goToFlights = () => {
  router.push({ name: 'Flights' })
}

const goToRegister = () => {
  router.push({ name: 'Register' })
}

const goToAbout = () => {
  router.push({ name: 'About' })
}

const goToDestination = (destination: any) => {
  router.push({
    name: 'FlightSearch',
    query: { to: destination.name }
  })
}

onMounted(() => {
  // Initialize any required data
})
</script>

<style lang="scss" scoped>
.home {
  min-height: 100vh;
}

.hero-section {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 80px 0;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  min-height: 600px;

  .hero-content {
    flex: 1;
    max-width: 600px;
    padding: 0 40px;
  }

  .hero-title {
    font-size: 3.5rem;
    font-weight: 700;
    margin-bottom: 20px;
    line-height: 1.2;

    .highlight {
      color: #ffd700;
    }
  }

  .hero-subtitle {
    font-size: 1.25rem;
    margin-bottom: 40px;
    opacity: 0.9;
    line-height: 1.6;
  }

  .hero-actions {
    display: flex;
    gap: 20px;

    .el-button {
      padding: 12px 24px;
      font-size: 1.1rem;
    }
  }

  .hero-image {
    flex: 1;
    display: flex;
    justify-content: center;
    align-items: center;

    img {
      max-width: 100%;
      height: auto;
    }
  }
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
}

.section-title {
  text-align: center;
  font-size: 2.5rem;
  font-weight: 700;
  margin-bottom: 60px;
  color: #2d3748;
}

.features-section {
  padding: 80px 0;
  background: #f7fafc;

  .features-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
    gap: 40px;
  }

  .feature-card {
    text-align: center;
    padding: 40px 20px;
    background: white;
    border-radius: 12px;
    box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
    transition: transform 0.3s ease, box-shadow 0.3s ease;

    &:hover {
      transform: translateY(-5px);
      box-shadow: 0 8px 25px rgba(0, 0, 0, 0.15);
    }

    .feature-icon {
      margin-bottom: 20px;
      color: #667eea;
    }

    .feature-title {
      font-size: 1.5rem;
      font-weight: 600;
      margin-bottom: 15px;
      color: #2d3748;
    }

    .feature-description {
      color: #718096;
      line-height: 1.6;
    }
  }
}

.search-section {
  padding: 80px 0;
  background: white;

  .search-form {
    max-width: 800px;
    margin: 0 auto;
    background: #f7fafc;
    padding: 40px;
    border-radius: 12px;
    box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);

    .search-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 20px;
      margin-bottom: 30px;
    }

    .search-input {
      width: 100%;
    }

    .search-actions {
      text-align: center;

      .el-button {
        padding: 12px 30px;
        font-size: 1.1rem;
      }
    }
  }
}

.destinations-section {
  padding: 80px 0;
  background: #f7fafc;

  .destinations-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
    gap: 30px;
  }

  .destination-card {
    background: white;
    border-radius: 12px;
    overflow: hidden;
    box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
    transition: transform 0.3s ease, box-shadow 0.3s ease;
    cursor: pointer;

    &:hover {
      transform: translateY(-5px);
      box-shadow: 0 8px 25px rgba(0, 0, 0, 0.15);
    }

    .destination-image {
      height: 200px;
      overflow: hidden;

      img {
        width: 100%;
        height: 100%;
        object-fit: cover;
      }
    }

    .destination-content {
      padding: 20px;

      .destination-name {
        font-size: 1.25rem;
        font-weight: 600;
        margin-bottom: 10px;
        color: #2d3748;
      }

      .destination-description {
        color: #718096;
        margin-bottom: 15px;
        line-height: 1.5;
      }

      .destination-price {
        display: flex;
        align-items: baseline;
        gap: 5px;

        .price-label {
          color: #718096;
          font-size: 0.9rem;
        }

        .price-amount {
          font-size: 1.5rem;
          font-weight: 700;
          color: #667eea;
        }
      }
    }
  }
}

.stats-section {
  padding: 80px 0;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;

  .stats-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
    gap: 40px;
    text-align: center;
  }

  .stat-item {
    .stat-number {
      font-size: 3rem;
      font-weight: 700;
      margin-bottom: 10px;
      color: #ffd700;

      .stat-suffix {
        font-size: 2rem;
      }
    }

    .stat-label {
      font-size: 1.1rem;
      opacity: 0.9;
    }
  }
}

.cta-section {
  padding: 80px 0;
  background: #2d3748;
  color: white;

  .cta-content {
    text-align: center;
    max-width: 600px;
    margin: 0 auto;

    h2 {
      font-size: 2.5rem;
      font-weight: 700;
      margin-bottom: 20px;
    }

    p {
      font-size: 1.1rem;
      margin-bottom: 40px;
      opacity: 0.9;
      line-height: 1.6;
    }

    .cta-actions {
      display: flex;
      gap: 20px;
      justify-content: center;

      .el-button {
        padding: 12px 30px;
        font-size: 1.1rem;
      }
    }
  }
}

// Responsive design
@media (max-width: 768px) {
  .hero-section {
    flex-direction: column;
    text-align: center;
    padding: 60px 20px;

    .hero-title {
      font-size: 2.5rem;
    }

    .hero-actions {
      flex-direction: column;
      align-items: center;
    }
  }

  .section-title {
    font-size: 2rem;
  }

  .search-form {
    padding: 20px;

    .search-grid {
      grid-template-columns: 1fr;
    }
  }

  .cta-actions {
    flex-direction: column;
    align-items: center;
  }
}
</style>
